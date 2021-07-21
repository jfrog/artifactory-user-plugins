import spock.lang.Specification
import groovy.json.JsonSlurper
import org.jfrog.artifactory.client.model.repository.settings.impl.PypiRepositorySettingsImpl
import org.jfrog.artifactory.client.ArtifactoryClientBuilder

class GetPypiMetadataTest extends Specification {
    def 'get pypi metadata test'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = ArtifactoryClientBuilder.create().setUrl(baseurl)
            .setUsername('admin').setPassword('password').build()
        def auth = "Basic ${'admin:password'.bytes.encodeBase64()}"

        def builder = artifactory.repositories().builders()
        def local = builder.localRepositoryBuilder().key('pypi-local')
        .repositorySettings(new PypiRepositorySettingsImpl()).build()
        artifactory.repositories().create(0, local)

        def file = new File('./src/test/groovy/GetPypiMetadataTest/pip-9.0.1-py2.py3-none-any.whl')
        artifactory.repository("pypi-local").upload("pip-9.0.1-py2.py3-none-any.whl", file).doUpload()

        when:
        def conn = new URL(baseurl + '/api/plugins/execute/getPypiMetadata?params=repoPath=/pip-9.0.1-py2.py3-none-any.whl%7CrepoKey=pypi-local').openConnection()
        conn.requestMethod = 'POST'
        conn.setRequestProperty('Authorization', auth)
        conn.getResponseCode()
        def output = conn.getInputStream().text

        then:
        output.contains("name='pip'")

        cleanup:
        artifactory.repository('pypi-local').delete()
    }
}
