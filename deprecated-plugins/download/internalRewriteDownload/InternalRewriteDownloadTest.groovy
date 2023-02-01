import spock.lang.Specification
import org.jfrog.artifactory.client.ArtifactoryClientBuilder
import org.jfrog.artifactory.client.model.repository.settings.impl.GenericRepositorySettingsImpl

class InternalRewriteDownloadTest extends Specification {

    def 'internalRewriteDownloadTest'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = ArtifactoryClientBuilder.create().setUrl(baseurl)
            .setUsername('admin').setPassword('password').build()

        def builder = artifactory.repositories().builders()
        def local = builder.localRepositoryBuilder().key('dist-local')
            .repositorySettings(new GenericRepositorySettingsImpl()).build()
            artifactory.repositories().create(0, local)

        def repo = artifactory.repository("dist-local")

        repo.file("/").properties().addProperty("latest.folderName","1.0").doSet()

        def myVersion = "1.0"

        when: 
        def file =  new ByteArrayInputStream('filecontents'.bytes) 
        def path ="$myVersion/test1.txt" 
        repo.upload(path, file).doUpload()
         def latestPath="latest/test1.txt"
        def downloadLatest = repo.download(latestPath).doDownload();

        then:
        def test = downloadLatest.text 
        test.contains('filecontents')

        cleanup: 
        repo.delete(path)
        repo.delete()

    }
}
