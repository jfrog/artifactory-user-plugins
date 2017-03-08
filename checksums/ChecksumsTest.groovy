import org.apache.commons.codec.digest.DigestUtils
import spock.lang.Specification

import org.jfrog.artifactory.client.model.repository.settings.impl.MavenRepositorySettingsImpl

import static org.jfrog.artifactory.client.ArtifactoryClient.create

class ChecksumsTest extends Specification {
    def 'checksums test'() {
        setup:
        def message = 'Lorem ipsum dolor sit amet'
        def checksum = DigestUtils.sha512Hex(message)
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = create(baseurl, 'admin', 'password')

        def builder = artifactory.repositories().builders()
        def local = builder.localRepositoryBuilder().key('maven-local')
        .repositorySettings(new MavenRepositorySettingsImpl()).build()
        artifactory.repositories().create(0, local)

        def repo = artifactory.repository('maven-local')
        def data = new ByteArrayInputStream(message.bytes)
        repo.upload('testfile', data).doUpload()

        when:
        def result = repo.download('testfile.sha512').doDownload().text

        then:
        checksum == result

        cleanup:
        repo.delete()
    }
}
