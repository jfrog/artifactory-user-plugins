import static org.jfrog.artifactory.client.ArtifactoryClient.create
import groovyx.net.http.HttpResponseException
import spock.lang.Specification

class DeleteEmptyDirsTest extends Specification {
    def 'delete empty dirs plugin test'() {
        setup:
        def artifactory = create("http://localhost:8088/artifactory", "admin", "password")
        artifactory.repository('libs-release-local').upload('some/path/file', new ByteArrayInputStream('test'.getBytes('utf-8'))).doUpload()
        artifactory.repository('libs-release-local').folder('some/other/path').create()

        when:
        "curl -X POST -uadmin:password http://localhost:8088/artifactory/api/plugins/execute/deleteEmptyDirsPlugin?params=paths=libs-release-local".execute().waitFor()
        artifactory.repository('libs-release-local').folder('some/other/path').info()

        then:
        thrown(HttpResponseException)
        artifactory.repository('libs-release-local').file('some/path/file').info()

        cleanup:
        artifactory.repository('libs-release-local').delete('some')
    }
}
