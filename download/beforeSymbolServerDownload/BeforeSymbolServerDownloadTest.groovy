import org.jfrog.lilypad.Control
import spock.lang.Specification
import org.jfrog.artifactory.client.ArtifactoryClientBuilder
import org.jfrog.artifactory.client.model.repository.settings.impl.NugetRepositorySettingsImpl
import org.artifactory.repo.*

class BeforeSymbolServerDownloadTest extends Specification {

    static final user = 'admin'
    static final password='password'
    static final userPassword="$user:$password"
    static final auth = "Basic ${userPassword.bytes.encodeBase64()}"
    static final url = "http://localhost:8081/artifactory/symbols"
    static final filePath ='wininet.pdb/7CE26DE332694328B6EB5F3C69DB20CC2/wininet.pd_'
    static final def remoteRepokey = 'microsoft-symbols'

    def 'before Symbol Server download test '() {
        setup:
        Control.setLoggerLevel(8088, 'org.apache.http.wire', 'debug')
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = ArtifactoryClientBuilder.create().setUrl(baseurl)
            .setUsername('admin').setPassword('password').build()
        def artifactory2 = ArtifactoryClientBuilder.create().setUrl('http://localhost:8081/artifactory')
            .setUsername('admin').setPassword('password').build()
        def builder = artifactory.repositories().builders()
        def builder2 = artifactory2.repositories().builders()

        def localRepo = builder2.localRepositoryBuilder().key('symbols').repositorySettings(new NugetRepositorySettingsImpl()).build()
        artifactory2.repositories().create(0, localRepo)
        def file = new ByteArrayInputStream('test symbol'.bytes)
        artifactory2.repository('symbols').upload(filePath, file).doUpload()

        def remoteRepo = builder.remoteRepositoryBuilder().key(remoteRepokey).url(url).repositorySettings(new NugetRepositorySettingsImpl()).build()
        artifactory.repositories().create(0, remoteRepo)

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

        then:
        textlog.contains("User-Agent: Microsoft-Symbol-Server/6.3.9600.17095")

        cleanup:
        artifactory.repository(remoteRepokey).delete()
        artifactory2.repository('symbols').delete()
    }
}
