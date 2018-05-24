import spock.lang.Specification
import org.jfrog.artifactory.client.model.repository.settings.impl.MavenRepositorySettingsImpl
import org.jfrog.artifactory.client.ArtifactoryClientBuilder
import groovyx.net.http.HttpResponseException

class NexusPushTest extends Specification {
    def 'nexus push test'() {
        setup:
        def artifactory_baseurl = 'http://localhost:8088/artifactory'
        def artifactory = ArtifactoryClientBuilder.create().setUrl(artifactory_baseurl)
            .setUsername('admin').setPassword('password').build()
        def artifactory_auth = "Basic ${'admin:password'.bytes.encodeBase64()}"

        def nexus_baseurl = 'http://localhost:8081/nexus'
        def nexus_auth = "Basic ${'admin:admin123'.bytes.encodeBase64()}"

        waitForNexus(nexus_baseurl, nexus_auth)

        def builder = artifactory.repositories().builders()
        def local = builder.localRepositoryBuilder().key('maven-local')
        .repositorySettings(new MavenRepositorySettingsImpl()).build()
        artifactory.repositories().create(0, local)

        def jarfile = new File('./src/test/groovy/NexusPushTest/guava-18.0.jar')
        artifactory.repository('maven-local').upload('com/google/guava/guava/18.0/guava-18.0.jar', jarfile).doUpload()

        when:
        def conn = new URL(nexus_baseurl + '/service/local/repositories').openConnection()
        conn.setRequestMethod('POST')
        conn.setRequestProperty('Authorization', nexus_auth)
        conn.doOutput = true
        conn.setRequestProperty('Content-Type', 'application/json')
        def textFile = '{"data" : {"id" : "maven-local",'
        textFile += '"name" : "maven-local",'
        textFile += '"repoType" : "hosted",'
        textFile += '"repoPolicy" : "RELEASE",'
        textFile += '"provider" : "maven2",'
        textFile += '"format" : "maven2",'
        textFile += '"providerRole" : "org.sonatype.nexus.proxy.repository.Repository",'
        textFile += '"exposed" : true,'
        textFile += '"indexable" : true,'
        textFile += '"browseable" : true}}'
        conn.outputStream.write(textFile.bytes)
        assert conn.getResponseCode() == 201

        conn = new URL(artifactory_baseurl + '/api/plugins/execute/nexusPush?params=stagingProfile=nexusPush%7Cclose=false%7Cdir=maven-local%2Fcom%2Fgoogle%2Fguava%2Fguava%2F18.0').openConnection()
        conn.setRequestMethod('POST')
        conn.setRequestProperty('Authorization', artifactory_auth)
        conn.doOutput = true
        conn.setRequestProperty('Content-Type', 'application/json')
        assert conn.getResponseCode() == 200

        conn = new URL(nexus_baseurl + '/service/local/artifact/maven/resolve?g=com.google.guava&a=guava&v=18.0&r=maven-local').openConnection()
        conn.setRequestMethod('GET')
        conn.setRequestProperty('Authorization', nexus_auth)
        conn.doOutput = true
        conn.setRequestProperty('Content-Type', 'application/json')
        assert conn.getResponseCode() == 200

        then:
        notThrown(HttpResponseException)

        cleanup:
        ignoringExceptions { artifactory.repository('maven-local').delete() }

        ignoringExceptions {
          conn = new URL(nexus_baseurl + '/service/local/repositories/maven-local').openConnection()
          conn.setRequestMethod('DELETE')
          conn.setRequestProperty('Authorization', nexus_auth)
          conn.doOutput = true
          conn.setRequestProperty('Content-Type', 'application/json')
          conn.getResponseCode()
        }
    }

    def waitForNexus(baseUrl, auth) {
        def initTime = System.currentTimeMillis()
        def response = 0
        while (response != 200 && System.currentTimeMillis() - initTime < 60000L) {
            println "Checking Nexus server status..."
            try {
                def conn = new URL("$baseUrl/service/local/status").openConnection()
                conn.setRequestMethod('GET')
                conn.setRequestProperty('Authorization', auth)
                conn.doOutput = true
                conn.setRequestProperty('Content-Type', 'application/json')
                response = conn.getResponseCode()
                println "Nexus server returned status $response!"
            } catch (Exception e) {
                println "Nexus server not available"
            }
            sleep(5000L)
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
