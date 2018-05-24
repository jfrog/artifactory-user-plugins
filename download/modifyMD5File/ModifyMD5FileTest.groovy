import org.apache.commons.codec.digest.DigestUtils
import spock.lang.Specification
import org.jfrog.artifactory.client.model.repository.settings.impl.NpmRepositorySettingsImpl
import org.jfrog.artifactory.client.ArtifactoryClientBuilder

class ModifyMD5FileTest extends Specification {
    def 'Modifymd5test'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory/'
        def artifactory = ArtifactoryClientBuilder.create().setUrl(baseurl)
            .setUsername('admin').setPassword('password').build()
        def builder = artifactory.repositories().builders()
        def local = builder.localRepositoryBuilder().key('md5-test-remote')
        .repositorySettings(new NpmRepositorySettingsImpl()).build()

        artifactory.repositories().create(0, local)

        def repo = artifactory.repository('md5-test-remote')

        def file = repo.upload('artifactory.txt', new ByteArrayInputStream('helloWorld'.bytes)).doUpload()

        def testfile = repo.upload('artifactory.txt.md5.txt', new ByteArrayInputStream('helloWorldall'.bytes)).doUpload()

        when:
        def testdownload = repo.download("artifactory.txt.md5").doDownload()

        def validatechecksum = repo.download("artifactory.txt.md5.txt").doDownload()

        then:
        testdownload.text == validatechecksum.text           //comparing both the String Values

        cleanup:
        repo.delete()
    }
}

