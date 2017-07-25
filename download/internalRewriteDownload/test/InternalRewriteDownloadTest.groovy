import spock.lang.Specification
import static org.jfrog.artifactory.client.ArtifactoryClient.create
import org.jfrog.artifactory.client.model.repository.settings.impl.GenericRepositorySettingsImpl


class InternalRewriteDownloadTest extends Specification {

    def 'test'() {
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
        
        //uploading first file
        def path ="$myVersion/test1.txt"
        def file = new File('./src/test/groovy/InternalRewriteDownloadTest/test1.txt')
        file.write "$myVersion"        
        repo.upload(path,file).doUpload();
            
        //uploading the file a second time 
        myVersion = "1.0"
        def path2 = "$myVersion/test1.txt"
        def file2 = new File('./src/test/groovy/InternalRewriteDownloadTest/test1.txt')
        file.write "$myVersion"
        repo.upload(path2,file2).doUpload();

        when: 
        //adding the property 'latest.folderName' with the value '1.0' to the root (dist-local)
        repo.file("/").properties().addProperty("latest.folderName","1.0").doSet()

        //downloading the most recent file from the 'latest' folder 
        repo.download("latest/test1.txt").doDownload();

        then:
        //checking to see if the test passed via output in the console 
        if(file2.readLines()[0]==myVersion) { 
           System.out.println("test passed")  
        } else{

            System.out.println("test failed")
        }
    }
}
