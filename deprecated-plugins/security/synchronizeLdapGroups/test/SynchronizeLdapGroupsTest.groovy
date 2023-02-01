import groovy.xml.XmlUtil
import org.apache.http.client.HttpResponseException
import org.jfrog.artifactory.client.model.Group
import org.jfrog.artifactory.client.model.Privilege
import org.jfrog.artifactory.client.model.repository.settings.impl.GenericRepositorySettingsImpl
import spock.lang.Shared
import spock.lang.Specification
import org.jfrog.lilypad.util.Docker

import org.jfrog.artifactory.client.ArtifactoryClientBuilder

class SynchronizeLdapGroupsTest extends Specification {

    static final baseurl = 'http://localhost:8088/artifactory'
    static final adminPassword = 'admin:password'
    static final auth = "Basic ${adminPassword.bytes.encodeBase64().toString()}"
    @Shared artifactory = ArtifactoryClientBuilder.create().setUrl(baseurl).setUsername('admin').setPassword('password').build()

    static final ldapPort = Docker.findPort()
    static final ldapBaseurl = "ldap://localhost:$ldapPort"
    static final ldapAdminUser = 'admin'
    static final ldapAdminPassword = 'admin'
    static final ldapUser = 'john'
    static final ldapUserPassword = 'johnldap'

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
        executeWithRetries "docker cp ${new File('./src/test/groovy/SynchronizeLdapGroupsTest/ldap_data.ldif').getAbsolutePath()} openldap:/"
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

    def 'ldap user uploading artifact to restricted repo test'() {
        setup:
        println 'Setting up test case...'
        // Setup ldap server
        runLdapServer()
        // Setup artifactory ldap configuration
        addLdapConfiguration()

        // Create repo
        def repoKey = 'repo-local'
        createLocalRepo(repoKey)

        // Create group
        def groupName = 'frogs'
        createGroup(groupName)

        // Create repo upload permission to group
        def permissionName = 'frogs-can-deploy-to-repo-local'
        grantRepoDeployPermissionToGroup(permissionName, groupName, repoKey)

        when:
        // Upload artifact to repo using ldap user
        def artifactoryLdapUser = ArtifactoryClientBuilder.create().setUrl(baseurl)
            .setUsername(ldapUser).setPassword(ldapUserPassword).build()
        def repo = artifactoryLdapUser.repository(repoKey)
        repo.upload("artifact", new ByteArrayInputStream("content".bytes)).doUpload()

        then:
        // Check if artifact was uploaded
        artifactExists(artifactory.repository(repoKey), 'artifact')

        cleanup:
        println 'Cleaning up test case...'
        // Delete permission
        ignoringExceptions{ artifactory.security().deletePermissionTarget(permissionName) }
        // Delete group
        ignoringExceptions{ artifactory.security().deleteGroup(groupName) }
        // Delete repo
        ignoringExceptions{ artifactory.repository(repoKey)?.delete() }
        // Revert ldap configuration settings
        ignoringExceptions{ removeLdapConfiguration() }
        // Remove ldap server
        ignoringExceptions{ shutdownLdapServer() }
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
                key 'ldap'
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
                enabledLdap 'ldap'
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
     * Grant deploy permission to group
     * @param permissionName
     * @param groupName
     * @param repoKey
     * @return
     */
    def grantRepoDeployPermissionToGroup (String permissionName, String groupName, String repoKey) {
        def principal = artifactory.security().builders().principalBuilder()
            .name(groupName)
            .privileges(Privilege.DEPLOY)
            .build()
        def principals = artifactory.security().builders().principalsBuilder()
            .groups(principal)
            .build()
        def permission = artifactory.security().builders().permissionTargetBuilder()
            .name(permissionName)
            .repositories(repoKey)
            .principals(principals)
            .build()
        artifactory.security().createOrReplacePermissionTarget(permission)
    }

    /**
     * Create security group
     * @param groupName
     * @return
     */
    def createGroup(String groupName) {
        Group group = artifactory.security().builders().groupBuilder()
                .name(groupName)
                .autoJoin(false)
                .description(groupName)
                .build()
        artifactory.security().createOrUpdateGroup(group)
    }

    /**
     * Create local repo
     * @param repoKey
     * @return
     */
    def createLocalRepo(String repoKey) {
        println "Creating local repo $repoKey"
        def builder = artifactory.repositories().builders()
        def local = builder.localRepositoryBuilder().key(repoKey)
                .repositorySettings(new GenericRepositorySettingsImpl()).build()
        artifactory.repositories().create(0, local)
    }

    /**
     * Get artifactory configuration
     * @return
     */
    private def getArtifactoryConfiguration(boolean retryIfNotAuthorized) {
        try {
            println 'Fetching artifactory configuration'
            def conn = new URL(baseurl + '/api/system/configuration').openConnection()
            conn.setRequestProperty('Authorization', auth)
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

    /**
     * Check if artifact exists
     * @param repo
     * @param path
     * @return
     */
    def artifactExists(repo, path) {
        println "Checking if artifact exists at $repo:$path..."
        return getFileInfo(repo, path) != null
    }

    /**
     * Get file artifact info
     * @param repo - Repository to look for artifact
     * @param path - Path to artifact
     * @return Artifact info or null if artifact cannot be found
     */
    def getFileInfo(repo, path) {
        try {
            return repo.file(path).info()
        } catch (HttpResponseException e) {
            e.printStackTrace()
        }
        return null
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
