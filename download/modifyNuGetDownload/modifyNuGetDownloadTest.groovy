import static org.jfrog.artifactory.client.ArtifactoryClient.create
import spock.lang.Specification

class ModifyNuGetDownLoadTest extends Specification {
    def 'test name'() {
        setup:
        def artifactory = create("http://localhost:8088/artifactory", "admin", "password")

        //when:

        //then:

        //cleanup:
    }
}
