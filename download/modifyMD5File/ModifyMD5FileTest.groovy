import org.apache.commons.codec.digest.DigestUtils
import spock.lang.Specification
import org.jfrog.artifactory.client.model.repository.settings.impl.NpmRepositorySettingsImpl
import static org.jfrog.artifactory.client.ArtifactoryClient.create

class ModifyMD5FileTest extends Specification {
    def 'Modifymd5test'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory/'
        def artifactory = create(baseurl, 'admin', 'password')
        def builder = artifactory.repositories().builders()
        def local = builder.localRepositoryBuilder().key('md5-test-repo')  
        .repositorySettings(new NpmRepositorySettingsImpl()).build()

        artifactory.repositories().create(0, local)
         
        def repo = artifactory.repository('list/md5-test-repo')          

        def file = repo.upload('artifactory.txt', new ByteArrayInputStream('helloWorld'.bytes)).doUpload()

        def testfile = repo.upload('artifactory.txt.md5.txt', new ByteArrayInputStream('helloWorldall'.bytes)).doUpload()
       
        when:
        def testdownload = artifactory.repository('list/md5-test-repo')
        .download("artifactory.txt.md5")
        .doDownload()

        def validatechecksum = artifactory.repository('list/md5-test-repo')
        .download("artifactory.txt.md5.txt")
        .doDownload()            
        
        then:
        testdownload == validatechecksum           //comparing both the String Values
    }
}

