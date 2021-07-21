import org.apache.http.client.HttpResponseException
import spock.lang.Specification
import org.jfrog.artifactory.client.ArtifactoryClientBuilder

import org.jfrog.artifactory.client.model.repository.settings.impl.MavenRepositorySettingsImpl

class ValidateArtifactLowerCaseTest extends Specification {
    def 'validate artifact lower case test'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = ArtifactoryClientBuilder.create().setUrl(baseurl)
            .setUsername('admin').setPassword('password').build()

        def builder1 = artifactory.repositories().builders()
        def local1 = builder1.localRepositoryBuilder().key('repositoryName1')
        .repositorySettings(new MavenRepositorySettingsImpl()).build()
        artifactory.repositories().create(0, local1)

        when:
        artifactory.repository('repositoryName1')
        .upload('file1', new ByteArrayInputStream('test'.getBytes('utf-8')))
        .doUpload()

        then:
        artifactory.repository("repositoryName1").file("file1").info()

        when:
        artifactory.repository('repositoryName1')
        .upload('FILE2', new ByteArrayInputStream('test'.getBytes('utf-8')))
        .doUpload()

        then:
        thrown(HttpResponseException)

        cleanup:
        artifactory.repository("repositoryName1").delete()
    }
}
