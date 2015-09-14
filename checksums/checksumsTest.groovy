import static org.jfrog.artifactory.client.ArtifactoryClient.create
import org.apache.commons.codec.digest.DigestUtils
import spock.lang.Specification

class ChecksumsTest extends Specification {
    def 'checksums test'() {
        setup:
        def message = 'Lorem ipsum dolor sit amet'
        def checksum = DigestUtils.sha512Hex(message)
        def artifactory = create("http://localhost:8088/artifactory", "admin", "password")
        def repo = artifactory.repository('libs-release-local')
        def data = new ByteArrayInputStream(message.bytes)
        repo.upload('testfile', data).doUpload()

        when:
        def result = repo.download('testfile.sha512').doDownload().text

        then:
        checksum == result

        cleanup:
        repo.delete('testfile')
    }
}
