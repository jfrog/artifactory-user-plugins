import spock.lang.Specification
import static org.jfrog.artifactory.client.ArtifactoryClient.create
import org.jfrog.artifactory.client.model.repository.settings.impl.NugetRepositorySettingsImpl
import org.apache.http.client.methods.*
import org.artifactory.repo.*
import groovyx.net.http.HTTPBuilder
import groovyx.net.http.HttpResponseException

import javax.xml.ws.http.HTTPBinding
import static groovyx.net.http.Method.GET
import static groovyx.net.http.ContentType.TEXT


class BeforeSymbolServerDownloadTest extends Specification {

    static final user = 'admin'
    static final password='password'
    static final userPassword="$user:$password"
    static final auth = "Basic ${userPassword.bytes.encodeBase64()}"
    static final url = "http://msdl.microsoft.com/download/symbols"
    static final filePath ='wininet.pdb/7CE26DE332694328B6EB5F3C69DB20CC2/wininet.pd_'
    def repokey = 'microsoft-symbols'

    def 'before Symbol Server download test '() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = create(baseurl, 'admin', 'password')
	def builder = artifactory.repositories().builders()

	def remoteRepo= builder.remoteRepositoryBuilder().key(repokey).url(url).repositorySettings(new NugetRepositorySettingsImpl()).build()
	artifactory.repositories().create(0,remoteRepo)
	
	when:
	def http = new HTTPBuilder("${baseurl}/${repokey}/${filePath}")
	http.setHeaders([Authorization: "${auth}"]) 

	then:
        def verified = http.request(GET, TEXT) { req ->
            response.success = { resp, inputStream ->
            }
	 }
	sleep(5000)

        when :
	def logfile ='http://localhost:8088/logs/artifactory.log'
  	def conn 
        conn = new URL (logfile).openConnection()
        conn.requestMethod = 'GET'
        conn.setRequestProperty('Authorization', auth)
        assert conn.responseCode == 200
        def reader = new InputStreamReader(conn.inputStream)
        def textlog = reader.text
        conn.disconnect()
	
	then:
	assert textlog.contains("User-Agent: Microsoft-Symbol-Server/6.3.9600.17095")==true
 
        cleanup:
	artifactory.repository(repokey).delete()
    }
}
