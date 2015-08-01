import static org.jfrog.artifactory.client.ArtifactoryClient.create
import groovyx.net.http.HttpResponseException
import spock.lang.Specification

class ArtifactCleanupTest extends Specification {
    def 'artifact cleanup test'() {
        setup:
        def artifactory = create("http://localhost:8088/artifactory", "admin", "password")
        def repo = artifactory.repository('libs-release-local')
        def file = new ByteArrayInputStream('test file'.bytes)
        repo.upload('test', file).doUpload()

        when:
        artifactory.plugins().execute('cleanup').
                withParameter('repos', 'libs-release-local').sync()
        repo.file('test').info()

        then:
        notThrown(HttpResponseException)

        when:
        artifactory.plugins().execute('cleanup').
                withParameter('repos', 'libs-release-local').
                withParameter('months', '0').sync()
        repo.file('test').info()

        then:
        thrown(HttpResponseException)
    }
}
