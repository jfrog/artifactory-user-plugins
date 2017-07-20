import org.apache.commons.codec.digest.DigestUtils
import spock.lang.Specification
import org.jfrog.artifactory.client.model.repository.settings.impl.NpmRepositorySettingsImpl
import static org.jfrog.artifactory.client.ArtifactoryClient.create

class ModifyMD5FileTest extends Specification {
    def 'test name'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory/'
        def artifactory = create(baseurl, 'admin', 'password')
        def builder = artifactory.repositories().builders()
        def local = builder.localRepositoryBuilder().key('npm')   // Creating an NPM repo
        .repositorySettings(new NpmRepositorySettingsImpl()).build()

        artifactory.repositories().create(0, local)
         
        def repo = artifactory.repository('list/npm')          

        def file = new File('./src/test/groovy/ModifyMD5FileTest/artifactory.txt')     //upload test file
        repo.upload('artifactory.txt',file).doUpload()

        def testfile = new File('./src/test/groovy/ModifyMD5FileTest/artifactory.txt.md5.txt')  //upload test file.md5.txt
        repo.upload('artifactory.txt.md5.txt',file).doUpload()
       
        when:
        def testdownload = artifactory.repository('list/npm')
        .download("artifactory.txt.md5")
        .doDownload()
        def checksum = DigestUtils.md5(testdownload)                       //calculate the download file checksum by requesting filename.txt.md5
        def validatechecksum = artifactory.repository('list/npm')
        .download("artifactory.txt.md5.txt")
        .doDownload()
        def finalchecksum = DigestUtils.md5(validatechecksum)             //calculate the filename.md5.txt checksum by requesting filename.txt.md5.txt
        
        then:
        checksum == finalchecksum                                         //comapring both the checksums
    }
}

