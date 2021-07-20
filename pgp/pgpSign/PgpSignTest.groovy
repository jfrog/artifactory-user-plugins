import spock.lang.Specification
import org.jfrog.artifactory.client.model.repository.settings.impl.MavenRepositorySettingsImpl
import org.jfrog.artifactory.client.ArtifactoryClientBuilder
import org.apache.http.client.HttpResponseException

class PgpSignTest extends Specification {
    def 'pgp sign test'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = ArtifactoryClientBuilder.create().setUrl(baseurl)
            .setUsername('admin').setPassword('password').build()

        def builder = artifactory.repositories().builders()
        def repository = builder.localRepositoryBuilder().key('maven-local')
        .repositorySettings(new MavenRepositorySettingsImpl()).build()
        artifactory.repositories().create(0, repository)

        def file = new ByteArrayInputStream('test'.bytes)
        artifactory.repository("maven-local").upload('test', file).doUpload()

        when:
        artifactory.repository("maven-local").file('test.asc').info()

        then:
        notThrown(HttpResponseException)

        cleanup:
        artifactory.repository("maven-local").delete()
    }
}
