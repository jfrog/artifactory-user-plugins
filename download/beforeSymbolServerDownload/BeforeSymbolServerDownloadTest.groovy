import spock.lang.Specification
import static org.jfrog.artifactory.client.ArtifactoryClient.create
import org.jfrog.artifactory.client.model.repository.settings.impl.NugetRepositorySettingsImpl
import groovyx.net.http.HttpResponseException
import org.artifactory.repo.*

class BeforeSymbolServerDownloadTest extends Specification {

    static final user = 'admin'
    static final password='password'
    static final userPassword="$user:$password"
    static final auth = "Basic ${userPassword.bytes.encodeBase64()}"
    static final url = "http://msdl.microsoft.com/download/symbols"
    static final filePath ='wininet.pdb/7CE26DE332694328B6EB5F3C69DB20CC2/wininet.pd_'
    static final def remoteRepokey = 'microsoft-symbols'

    def 'before Symbol Server download test '() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = create(baseurl, 'admin', 'password')
	def builder = artifactory.repositories().builders()

	def remoteRepo= builder.remoteRepositoryBuilder().key(remoteRepokey).url(url).repositorySettings(new NugetRepositorySettingsImpl()).build()
	artifactory.repositories().create(0,remoteRepo)
	
	when:
        artifactory.repository(remoteRepokey).download(filePath).doDownload();

	def logfile ='http://localhost:8088/artifactory/api/systemlogs/downloadFile?id=artifactory.log'
  	def conn = new URL (logfile).openConnection()
        conn.requestMethod = 'GET'
        conn.setRequestProperty('Authorization', auth)
        assert conn.responseCode == 200
        def reader = new InputStreamReader(conn.inputStream)
        def textlog = reader.text
        conn.disconnect()
	textlog.contains("User-Agent: Microsoft-Symbol-Server/6.3.9600.17095")

	then:
	notThrown(HttpResponseException)
 
        cleanup:
	artifactory.repository(remoteRepokey).delete()
    }
}
