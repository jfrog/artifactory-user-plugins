import groovy.json.JsonSlurper
import groovy.xml.XmlUtil
import spock.lang.Shared
import spock.lang.Specification
import org.jfrog.lilypad.util.Docker

import org.jfrog.artifactory.client.ArtifactoryClientBuilder

class SlashedADTest extends Specification {

    static final baseurl = 'http://localhost:8088/artifactory'
    static final adminPassword = 'admin:password'
    static final auth = "Basic ${adminPassword.bytes.encodeBase64().toString()}"
    @Shared artifactory = ArtifactoryClientBuilder.create().setUrl(baseurl).setUsername('admin').setPassword('password').build()

    static final ldapName = 'ldap'
    static final ldapPort = Docker.findPort()
    static final ldapBaseurl = "ldap://localhost:$ldapPort"
    static final ldapAdminUser = 'admin'
    static final ldapAdminPassword = 'admin'

    static final ldapUser = 'john'
    static final ldapUserPassword = 'johnldap'

    static final slashedLdapUser = "$ldapName+$ldapUser"
    static final slashedLdapUserEncoded = URLEncoder.encode(slashedLdapUser, "UTF-8")

    /**
     * Setup open ldap server
     * @return
     */
    def runLdapServer() {
        // Run docker container
        executeAndPrintOutput"docker run --name openldap -p $ldapPort:389 -d osixia/openldap:1.1.9"
        // Wait for ldap to be available
        waitForLdapServer()
        // Copy ldap data file to container
        executeWithRetries "docker cp ${new File('./src/test/groovy/SlashedADTest/ldap_data.ldif').getAbsolutePath()} openldap:/"
        // Import data to ldap
        executeWithRetries "docker exec openldap ldapadd -x -H ldap://localhost -D cn=admin,dc=example,dc=org -w admin -f /ldap_data.ldif"
    }

    /**
     * Wait for ldap server to be available
     */
    def waitForLdapServer() {
        def initTime = System.currentTimeMillis()
        def status = 255
        // Repeat until success response is received or one minute timeout is reached
        while (status == 255 && System.currentTimeMillis() - initTime < 60000L) {
            status = executeAndPrintOutput("docker exec openldap ldapsearch -x -H ldap://localhost -D cn=admin,dc=example,dc=org -w admin")
            sleep(1000)
        }
    }

    /**
     * Remove open ldap server
     * @return
     */
    def shutdownLdapServer() {
        executeAndPrintOutput "docker rm -f openldap"
    }

    def 'slashed ldap user gets api key test'() {
        setup:
        println 'Setting up test case...'
        // Setup ldap server
        runLdapServer()
        // Setup artifactory ldap configuration
        addLdapConfiguration()

        when:
        // Create API key for the slashed ldap user
        def apiKey = createAPIKey(slashedLdapUser, ldapUserPassword)

        then:
        // Check if API key was created
        apiKey != null

        cleanup:
        println 'Cleaning up test case...'
        // Delete ldap user
        ignoringExceptions { artifactory.security().deleteUser(slashedLdapUserEncoded) }
        // Revert ldap configuration settings
        ignoringExceptions { removeLdapConfiguration() }
        // Remove ldap server
        ignoringExceptions { shutdownLdapServer() }
    }

    def createAPIKey(user, password) {
        try {
            println "Requesting API Key creation for user $user..."
            def conn = new URL(baseurl + '/api/security/apiKey').openConnection()
            conn.setRequestProperty('Authorization', "Basic ${(user + ':' + password).bytes.encodeBase64().toString()}")
            conn.setDoOutput(true)
            conn.setRequestMethod('POST')
            println "Response code ${conn.responseCode}"
            assert conn.responseCode == 201
            return new JsonSlurper().parse(conn.inputStream).apiKey
        } catch (Exception e) {
            e.printStackTrace()
            throw e
        }
    }

    /**
     * Add ldap configuration
     * @return
     */
    private def addLdapConfiguration() {
        println 'Setting up LDAP integration...'
        def config = getArtifactoryConfiguration(true)
        def newConfig = new XmlSlurper(false, false).parseText(config)

        newConfig.security.ldapSettings.appendNode {
            ldapSetting {
                key ldapName
                enabled 'true'
                ldapUrl "$ldapBaseurl/dc=example,dc=org"
                userDnPattern 'uid={0},ou=People'
                search {
                    searchFilter 'uid={0}'
                    searchSubTree 'true'
                    managerDn "cn=$ldapAdminUser,dc=example,dc=org"
                    managerPassword ldapAdminPassword
                }
                autoCreateUser 'false'
                emailAttribute 'mail'
                ldapPoisoningProtection 'true'
            }
        }

        newConfig.security.ldapGroupSettings.appendNode {
            ldapGroupSetting {
                name 'il-users'
                groupBaseDn null
                groupNameAttribute 'cn'
                groupMemberAttribute 'memberUid'
                subTree 'true'
                filter '(objectClass=posixGroup)'
                descriptionAttribute 'cn'
                strategy 'STATIC'
                enabledLdap ldapName
            }
        }

        saveArtifactoryConfiguration(XmlUtil.serialize(newConfig))
    }

    /**
     * Remove ldap configuration
     * @return
     */
    private def removeLdapConfiguration() {
        println 'Removing LDAP integration...'
        def config = getArtifactoryConfiguration(true)
        def newConfig = new XmlSlurper().parseText(config)

        newConfig.security.ldapSettings = null
        newConfig.security.ldapGroupSettings = null

        saveArtifactoryConfiguration(XmlUtil.serialize(newConfig))
    }

    /**
     * Get artifactory configuration
     * @return
     */
    def getArtifactoryConfiguration(retryIfNotAuthorized, authorization = auth) {
        try {
            println 'Fetching artifactory configuration'
            def conn = new URL(baseurl + '/api/system/configuration').openConnection()
            conn.setRequestProperty('Authorization', authorization)
            def responseCode = conn.responseCode
            println "Response code: $responseCode"
            if (responseCode == 401 && retryIfNotAuthorized) {
                // If not authorized maybe thats because the ldap configuration was removed
                // In that case, another attempt may succeed since the user will be identified as internal after
                // the first attempt
                return getArtifactoryConfiguration(false)
            }
            def config = conn.inputStream.text
            return config
        } catch (Exception e) {
            e.printStackTrace()
            throw e
        }
    }

    /**
     * Save artifactory configuration
     * @param config
     */
    private def saveArtifactoryConfiguration(config) {
        try {
            println 'Saving artifactory configuration...'
            def conn = new URL(baseurl + '/api/system/configuration').openConnection()
            conn.setRequestProperty('Authorization', auth)
            conn.setDoOutput(true)
            conn.setRequestMethod('POST')
            conn.setRequestProperty('Content-Type', 'application/xml')
            conn.getOutputStream().write(config.bytes)
            println "Response code ${conn.responseCode}"
            conn.responseCode
        } catch (Exception e) {
            e.printStackTrace()
            throw e
        }
    }

    def executeAndPrintOutput(command) {
        println "Executing: $command"
        def proc = command.execute()
        proc.consumeProcessOutput(System.out, System.err)
        proc.waitFor()
        println "Exit: ${proc.exitValue()}"
        return proc.exitValue()
    }

    def executeWithRetries (command, expectedResponse = 0, maxTries = 10) {
        def response = -1
        def tries = 0
        while (response != expectedResponse && tries < maxTries) {
            response = executeAndPrintOutput(command)
            tries++
            sleep(1000)
        }
    }

    def ignoringExceptions = { method ->
        try {
            method()
        } catch (Exception e) {
            e.printStackTrace()
        }
    }
}
