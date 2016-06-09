import spock.lang.Specification
import static org.jfrog.artifactory.client.ArtifactoryClient.create

class CreateCopyTest extends Specification {
	def 'create copy test'() {
		setup:
		def baseurl = 'http://localhost:8088/artifactory'
		def artifactory = create(baseurl, 'admin', 'password')
		def file = new File('/home/auser/weatherr_0.1.2.tar.gz')

		when:
		//create item in local
		artifactory.repository("libs-release-local")
		.upload("a/test/path/weatherr_0.1.2.tar.gz", file)
		.withProperty("prop", "test")
		.doUpload();

		then:
		//make sure the item was copied to copy repo with path/info
		artifactory.repository("libs-release-copy").file("a/test/path/weatherr_0.1.2.tar.gz").info();

		cleanup:
		//delete items
		artifactory.repository("libs-release-local").delete("a/test/path/weatherr_0.1.2.tar.gz");
		artifactory.repository("libs-release-copy").delete("a/test/path/weatherr_0.1.2.tar.gz");
	}
}
