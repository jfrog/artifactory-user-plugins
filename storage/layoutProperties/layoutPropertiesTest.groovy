import static org.jfrog.artifactory.client.ArtifactoryClient.create
import spock.lang.Specification

class LayoutPropertiesTest extends Specification {
    def 'maven layout properties plugin test'() {
        setup:
        def artifactory = create("http://localhost:8088/artifactory", "admin", "password")
        expect:
        artifactory.repository('libs-release-local').upload('org/test/modname/1.0/modname-1.0.txt', new ByteArrayInputStream('test'.getBytes('utf-8'))).doUpload()
    }
}
