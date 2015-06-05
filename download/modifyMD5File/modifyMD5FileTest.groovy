import static org.jfrog.artifactory.client.ArtifactoryClient.create

/**
 * Created by freds on 8/4/14.
 */
class modifyMD5FileTest {
    static def startArtifactory() {
        def artifactory = create("http://localhost:8088/artifactory", "admin", "password")
        // TODO: Fill out the tests
    }
}
