import groovyx.net.http.HttpResponseException
import spock.lang.Specification

import org.jfrog.artifactory.client.model.repository.settings.impl.MavenRepositorySettingsImpl

import static org.jfrog.artifactory.client.ArtifactoryClient.create

class DeleteEmptyDirsTest extends Specification {
    def 'delete empty dirs plugin test'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = create(baseurl, 'admin', 'password')

        def builder = artifactory.repositories().builders()
        def local = builder.localRepositoryBuilder().key('maven-local')
        .repositorySettings(new MavenRepositorySettingsImpl()).build()
        artifactory.repositories().create(0, local)

        artifactory.repository('maven-local').upload('some/path/file', new ByteArrayInputStream('test'.getBytes('utf-8'))).doUpload()
        artifactory.repository('maven-local').folder('some/other/path').create()

        when:
        "curl -X POST -uadmin:password http://localhost:8088/artifactory/api/plugins/execute/deleteEmptyDirsPlugin?params=paths=maven-local".execute().waitFor()
        artifactory.repository('maven-local').folder('some/other/path').info()

        then:
        thrown(HttpResponseException)
        artifactory.repository('maven-local').file('some/path/file').info()

        cleanup:
        artifactory.repository('maven-local').delete()
    }
}
