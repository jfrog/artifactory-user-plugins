
import spock.lang.Specification
import static org.jfrog.artifactory.client.ArtifactoryClient.create

class PreventOverrideTest extends Specification {
    def 'create copy test'() {
        setup:
        def baseurl = 'http://localhost:8081/artifactory'
        def artifactory = create(baseurl, 'non_admin', 'password')
        def file = new File('/Users/alexeiv/Documents/temp/npm/test/test.txt')

        when:
        //create item in local
        artifactory.repository("libs-release-local")
                .upload("a/test/path/test.txt", file)
                .withProperty("prop", "test")
                .doUpload();

        then:
        //deploy the file again with the non admin user, the upload should fail
        artifactory.repository("libs-release-local")
                .upload("a/test/path/test.txt", file)
                .withProperty("prop", "test")
                .doUpload();
    }
}
