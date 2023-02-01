import spock.lang.Specification
import org.jfrog.artifactory.client.model.repository.settings.impl.MavenRepositorySettingsImpl
import org.jfrog.artifactory.client.ArtifactoryClientBuilder
import groovy.util.XmlSlurper

class SynchronizeUserKeysTest extends Specification {
    def 'synchronize user keys test'() {
        setup:
        def baseurl1 = 'http://localhost:8088/artifactory'
        def artifactory1 = ArtifactoryClientBuilder.create().setUrl(baseurl1).setUsername('admin').setPassword('password').build()

        def baseurl2 = 'http://localhost:8081/artifactory'
        def artifactory2 = ArtifactoryClientBuilder.create().setUrl(baseurl2).setUsername('admin').setPassword('password').build()
        def auth = "Basic ${'admin:password'.bytes.encodeBase64().toString()}"

        def builder = artifactory1.repositories().builders()
        def local = builder.localRepositoryBuilder().key('maven-local')
        .repositorySettings(new MavenRepositorySettingsImpl()).build()
        artifactory1.repositories().create(0, local)

        artifactory1.repository('maven-local').upload('settings.xml', new ByteArrayInputStream('apikey'.getBytes('utf-8'))).doUpload()

        when:
        def settings = artifactory1.repository('maven-local').download('settings.xml').doDownload()

        def conn = new URL(baseurl1 + '/api/system/security').openConnection()
        conn.setRequestMethod('GET')
        conn.doOutput = true
        conn.setRequestProperty('Authorization', auth)
        conn.setRequestProperty('Content-Type', 'application/json')
        conn.getResponseCode()
        def output = new XmlSlurper().parse(conn.getInputStream())
        def publickey1 = output.security.publicKey
        def privatekey1 = output.security.privateKey

        conn = new URL(baseurl2 + '/api/system/security').openConnection()
        conn.setRequestMethod('GET')
        conn.doOutput = true
        conn.setRequestProperty('Authorization', auth)
        conn.setRequestProperty('Content-Type', 'application/json')
        conn.getResponseCode()
        output = new XmlSlurper().parse(conn.getInputStream())
        def publickey2 = output.security.publicKey
        def privatekey2 = output.security.privateKey

        then:
        publickey1 == publickey2
        privatekey1 == privatekey2

        cleanup:
        artifactory1.repository('maven-local').delete()
    }
}
