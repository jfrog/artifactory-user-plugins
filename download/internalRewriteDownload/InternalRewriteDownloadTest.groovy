import spock.lang.Specification
import static org.jfrog.artifactory.client.ArtifactoryClient.create
import org.jfrog.artifactory.client.model.repository.settings.impl.GenericRepositorySettingsImpl

class InternalRewriteDownloadTest extends Specification {

    def 'internalRewriteDownloadTest'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = create(baseurl, 'admin', 'password')
        
        //building dist-local repo
        def builder = artifactory.repositories().builders()
        def local = builder.localRepositoryBuilder().key('dist-local')
            .repositorySettings(new GenericRepositorySettingsImpl()).build()
            artifactory.repositories().create(0, local)

        def repo = artifactory.repository("dist-local")

        def myVersion = "VERSION" 
        myVersion = "latest"
        
        //uploading test file
        def path ="$myVersion/test1.txt"    
        repo.upload(path, new ByteArrayInputStream('filecontents'.bytes)).doUpload()

        when: 
        //adding the property 'latest.folderName' with the value 'latest' to the root (dist-local)
        repo.file("/").properties().addProperty("latest.folderName","latest").doSet()

        //downloading the most recent file from the 'latest' folder 
        repo.download("latest/test1.txt").doDownload();

        then:
        assert path.readLines().contains('latest/test1.txt')

    }
}
