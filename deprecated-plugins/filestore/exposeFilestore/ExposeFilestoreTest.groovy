import spock.lang.Specification
import org.jfrog.lilypad.Control
import org.jfrog.artifactory.client.ArtifactoryClientBuilder
import org.jfrog.artifactory.client.model.repository.settings.impl.MavenRepositorySettingsImpl

class ExposeFilestoreTest extends Specification {
    def 'expose filestore test'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = ArtifactoryClientBuilder.create().setUrl(baseurl)
            .setUsername('admin').setPassword('password').build()
        def auth = "Basic ${'admin:password'.bytes.encodeBase64().toString()}"
        
        def builder = artifactory.repositories().builders()
        def local = builder.localRepositoryBuilder().key('maven-local')
        .repositorySettings(new MavenRepositorySettingsImpl()).build()
        artifactory.repositories().create(0, local)

        artifactory.repository('maven-local').upload('foo/bar/file', new ByteArrayInputStream('test'.getBytes('utf-8')))
        .withProperty("expose", "true")
        .doUpload()

        when:
        def conn = new URL(baseurl + '/api/plugins/execute/exposeRepository?params=repo=maven-local%7Cdest=/tmp').openConnection()
        conn.setRequestMethod('POST')
        conn.doOutput = true
        conn.setRequestProperty('Authorization', auth)
        conn.setRequestProperty('Content-Type', 'application/json')
        conn.getResponseCode()

        then:
        Control.fileExists(8088, '/tmp/foo/bar/file')

        cleanup:
        artifactory.repository('maven-local').delete()
    }
}
