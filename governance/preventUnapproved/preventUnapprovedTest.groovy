import static org.jfrog.artifactory.client.ArtifactoryClient.create
import groovyx.net.http.HttpResponseException
import spock.lang.Specification

class PreventUnapprovedTest extends Specification {
    def 'prevent unapproved test'() {
        setup:
        def artifactory = create("http://localhost:8088/artifactory", "admin", "password")
        def repo = artifactory.repository('libs-release-local')

        when:
        def artifact = new ByteArrayInputStream("$status artifact".bytes)
        repo.upload(status, artifact).doUpload()
        repo.file(status).properties().addProperty('approver.status', status).doSet()

        then:
        testDownload(repo, status, "$status artifact", approved)

        cleanup:
        repo.delete(status)

        where:
        status     | approved
        'approved' | true
        'rejected' | false
    }

    def testDownload(repo, status, content, approved) {
        try {
            repo.download(status).doDownload().text == content && approved
        } catch (HttpResponseException ex) {
            !approved
        }
    }
}
