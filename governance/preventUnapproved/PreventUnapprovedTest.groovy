import groovyx.net.http.HttpResponseException
import spock.lang.Specification

import org.jfrog.artifactory.client.model.repository.settings.impl.MavenRepositorySettingsImpl
import static org.jfrog.artifactory.client.ArtifactoryClient.create

class PreventUnapprovedTest extends Specification {
    def 'prevent unapproved test'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = create(baseurl, 'admin', 'password')

        def builder = artifactory.repositories().builders()
        def local = builder.localRepositoryBuilder().key('maven-local')
        .repositorySettings(new MavenRepositorySettingsImpl()).build()
        artifactory.repositories().create(0, local)

        when:
        def artifact = new ByteArrayInputStream("$status artifact".bytes)
        artifactory.repository('maven-local').upload(status, artifact).doUpload()
        artifactory.repository('maven-local').file(status).properties().addProperty('approver.status', status).doSet()

        then:
        testDownload(artifactory.repository('maven-local'), status, "$status artifact", approved)

        cleanup:
        artifactory.repository('maven-local').delete()

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
