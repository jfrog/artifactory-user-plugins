import spock.lang.Specification
import groovy.json.JsonSlurper

import org.jfrog.artifactory.client.ArtifactoryClientBuilder
import org.jfrog.artifactory.client.model.repository.settings.impl.MavenRepositorySettingsImpl

class NewFileWorkflowTest extends Specification {
    def 'new file workflow test'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = ArtifactoryClientBuilder.create().setUrl(baseurl)
            .setUsername('admin').setPassword('password').build()

        def builder = artifactory.repositories().builders()
        def local = builder.localRepositoryBuilder().key('maven-local')
        .repositorySettings(new MavenRepositorySettingsImpl()).build()
        artifactory.repositories().create(0, local)

        when:
        artifactory.repository('maven-local').upload('pass', new ByteArrayInputStream('B'.getBytes('utf-8'))).doUpload()
        sleep(21000)

        def auth = "Basic ${'admin:password'.bytes.encodeBase64()}"
        def conn = new URL("${baseurl}/api/storage/maven-local/pass?properties").openConnection()
        conn.requestMethod = 'GET'
        conn.setRequestProperty('Authorization', auth)
        assert conn.responseCode == 200
        def json = new JsonSlurper().parse(conn.getInputStream())
        conn.disconnect()

        then:
        json.properties['workflow.status'] == ['EXECUTED']

        when:
        artifactory.repository('maven-local').upload('fail', new ByteArrayInputStream('A'.getBytes('utf-8'))).doUpload()
        sleep(21000)

        conn = new URL("${baseurl}/api/storage/maven-local/fail?properties").openConnection()
        conn.requestMethod = 'GET'
        conn.setRequestProperty('Authorization', auth)
        assert conn.responseCode == 200
        json = new JsonSlurper().parse(conn.getInputStream())
        conn.disconnect()

        then:
        json.properties['workflow.status'] == ['FAILED_EXECUTION']

        cleanup:
        artifactory.repository('maven-local').delete()
    }
}
