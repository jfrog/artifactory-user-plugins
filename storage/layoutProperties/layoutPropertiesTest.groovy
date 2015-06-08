import static org.jfrog.artifactory.client.ArtifactoryClient.create
import spock.lang.Specification

class LayoutPropertiesTest extends Specification {
    def 'maven layout properties plugin test'() {
        setup:
        def repo = 'libs-release-local'
        def path = 'org/test/modname/1.0/modname-1.0.txt'
        def artifactory = create("http://localhost:8088/artifactory", "admin", "password")

        when:
        def stream = new ByteArrayInputStream('test'.getBytes('utf-8'))
        artifactory.repository(repo).upload(path, stream).doUpload()
        def props = artifactory.repository(repo).file(path).getProperties('')

        then:
        props['layout.organization'] == ['org.test']
        props['layout.module'] == ['modname']
        props['layout.baseRevision'] == ['1.0']

        cleanup:
        artifactory.repository(repo).delete('org')
    }
}
