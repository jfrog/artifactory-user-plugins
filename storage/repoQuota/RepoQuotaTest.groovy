import spock.lang.Specification
import groovyx.net.http.HttpResponseException

import static org.jfrog.artifactory.client.ArtifactoryClient.create
import org.jfrog.artifactory.client.model.repository.settings.impl.MavenRepositorySettingsImpl

class RepoQuotaTest extends Specification {
    def 'repo quota test'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = create(baseurl, 'admin', 'password')

        def builder = artifactory.repositories().builders()
        def local = builder.localRepositoryBuilder().key('maven-local')
        .repositorySettings(new MavenRepositorySettingsImpl()).build()
        artifactory.repositories().create(0, local)

        println "Sending quota request to artifactory"
        def auth = "Basic ${'admin:password'.bytes.encodeBase64()}"
        def conn = new URL("${baseurl}/api/storage/maven-local?properties=repository.path.quota=5").openConnection()
        conn.requestMethod = 'PUT'
        conn.setRequestProperty('Authorization', auth)
        assert conn.responseCode == 204
        println "Response code : " + conn.responseCode
        conn.disconnect()
        sleep 10
        
        when:
        println "Upload first file - will upload"
        artifactory.repository('maven-local').upload('file1', new ByteArrayInputStream('test1'.getBytes('utf-8'))).doUpload()
        println "Upload to fail due to quota"
        artifactory.repository('maven-local').upload('file2', new ByteArrayInputStream('test2'.getBytes('utf-8'))).doUpload()

        then:
        thrown(HttpResponseException)
        artifactory.repository('maven-local').file('file1').info()

        cleanup:
        artifactory.repository("maven-local").delete()
    }
}
