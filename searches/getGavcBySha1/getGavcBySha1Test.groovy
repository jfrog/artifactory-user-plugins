import spock.lang.Specification

import static org.jfrog.artifactory.client.ArtifactoryClient.create

class GetGavcBySha1Test extends Specification {
    def 'test name'() {
        setup:
        def artifactory = create("http://localhost:8088/artifactory", "admin", "password")

        // when:

        // then:

        // cleanup:
    }
}
