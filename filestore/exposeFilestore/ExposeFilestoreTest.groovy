import spock.lang.Specification
import java.nio.file.Files
import java.nio.file.Paths
import static org.jfrog.artifactory.client.ArtifactoryClient.create
import org.jfrog.artifactory.client.model.repository.settings.impl.MavenRepositorySettingsImpl

class ExposeFilestoreTest extends Specification {
    def 'expose filestore test'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = create(baseurl, 'admin', 'password')
        def auth = "Basic ${'admin:password'.bytes.encodeBase64().toString()}"
        
        def builder = artifactory.repositories().builders()
        def local = builder.localRepositoryBuilder().key('maven-local')
        .repositorySettings(new MavenRepositorySettingsImpl()).build()
        artifactory.repositories().create(0, local)

        artifactory.repository('maven-local').upload('foo/bar/file', new ByteArrayInputStream('test'.getBytes('utf-8')))
        .withProperty("expose", "true")
        .doUpload()

        when:
        println "filestoreexpose"
        println "Plugin Loaded is " + artifactory.plugins().list()
        def conn = new URL(baseurl + '/api/plugins/execute/exposeRepository?params=repo=maven-local%7Cdest=/tmp').openConnection()
        conn.setRequestMethod('POST')
        conn.doOutput = true
        conn.setRequestProperty('Authorization', auth)
        conn.setRequestProperty('Content-Type', 'application/json')

        then:
        println "Filestoreexpose checking sys link"
        Files.isSymbolicLink(Paths.get('/tmp/foo/bar/file'))

        cleanup:
        artifactory.repository('maven-local').delete()
    }
}
