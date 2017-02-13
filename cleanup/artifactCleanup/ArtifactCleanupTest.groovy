import spock.lang.Specification
import org.jfrog.artifactory.client.model.repository.settings.impl.MavenRepositorySettingsImpl
import groovyx.net.http.HttpResponseException

import static org.jfrog.artifactory.client.ArtifactoryClient.create

class ArtifactCleanupTest extends Specification {
    def 'artifact cleanup test'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = create(baseurl, 'admin', 'password')

        def builder = artifactory.repositories().builders()
        def repository = builder.localRepositoryBuilder().key('maven-local')
        .repositorySettings(new MavenRepositorySettingsImpl()).build()
        artifactory.repositories().create(0, repository)

        def file = new ByteArrayInputStream('test'.bytes)
        artifactory.repository("maven-local").upload('test', file).doUpload()

        when:
        artifactory.plugins().execute('cleanup').
            withParameter('repos', 'maven-local').sync()
        artifactory.repository("maven-local").file('test').info()

        then:
        notThrown(HttpResponseException)

        when:
        artifactory.plugins().execute('cleanup').
            withParameter('repos', 'maven-local').
            withParameter('months', '0').sync()
        artifactory.repository("maven-local").file('test').info()

        then:
        thrown(HttpResponseException)

        cleanup:
        artifactory.repository("maven-local").delete()
    }
}