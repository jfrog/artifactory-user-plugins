import spock.lang.Specification
import org.jfrog.artifactory.client.model.repository.settings.impl.MavenRepositorySettingsImpl
import org.jfrog.artifactory.client.ArtifactoryClientBuilder

class ExecuteFromFilestoreTest extends Specification {
    def 'execute from filestore test'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def auth = "Basic ${'admin:password'.bytes.encodeBase64().toString()}"
        def artifactory = ArtifactoryClientBuilder.create().setUrl(baseurl)
            .setUsername('admin').setPassword('password').build()


        def builder = artifactory.repositories().builders()
        def local = builder.localRepositoryBuilder().key('maven-local')
        .repositorySettings(new MavenRepositorySettingsImpl()).build()
        artifactory.repositories().create(0, local)

        artifactory.repository('maven-local').upload('foo/bar/file', new ByteArrayInputStream('test'.getBytes('utf-8'))).doUpload()

        when:
        def conn = new URL(baseurl + '/api/plugins/execute/copyAndExecute').openConnection()
        conn.setRequestMethod('POST')
        conn.doOutput = true
        conn.setRequestProperty('Authorization', auth)
        conn.setRequestProperty('Content-Type', 'application/json')
        def textFile = new File('./src/test/groovy/ExecuteFromFilestoreTest/execCommand.json')
        conn.outputStream.write(textFile.bytes)
        conn.getResponseCode()
        def output = conn.getInputStream().text

        then:
        output.readLines().contains('/tmp/foo/bar/file')

        cleanup:
        artifactory.repository('maven-local').delete()
    }
}
