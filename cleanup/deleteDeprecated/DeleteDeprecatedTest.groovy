import org.apache.http.client.HttpResponseException
import spock.lang.Specification

import org.jfrog.artifactory.client.model.repository.settings.impl.MavenRepositorySettingsImpl
import org.jfrog.artifactory.client.ArtifactoryClientBuilder

class DeleteDeprecatedTest extends Specification {
    def 'delete deprecated plugin test'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = ArtifactoryClientBuilder.create().setUrl(baseurl)
            .setUsername('admin').setPassword('password').build()

        def builder = artifactory.repositories().builders()
        def local = builder.localRepositoryBuilder().key('maven-local')
        .repositorySettings(new MavenRepositorySettingsImpl()).build()
        artifactory.repositories().create(0, local)

        artifactory.repository('maven-local').upload('some/path1/file1', new ByteArrayInputStream('test1'.getBytes('utf-8'))).doUpload()
        artifactory.repository('maven-local').upload('some/path2/file2', new ByteArrayInputStream('test2'.getBytes('utf-8'))).doUpload()
        artifactory.repository('maven-local').file('some/path2/file2').properties().addProperty('analysis.deprecated', 'true').doSet()

        when:
        "curl -X POST -uadmin:password http://localhost:8088/artifactory/api/plugins/execute/deleteDeprecatedPlugin".execute().waitFor()
        artifactory.repository('maven-local').file('some/path2/file2').info()

        then:
        thrown(HttpResponseException)
        artifactory.repository('maven-local').file('some/path1/file1').info()

        cleanup:
        artifactory.repository('maven-local').delete()
    }
}
