import spock.lang.Specification
import org.jfrog.artifactory.client.model.repository.settings.impl.MavenRepositorySettingsImpl
import spock.lang.Specification
import static org.jfrog.artifactory.client.ArtifactoryClient.create
import groovy.json.JsonSlurper
import org.jfrog.artifactory.client.ArtifactoryRequest
import org.jfrog.artifactory.client.impl.ArtifactoryRequestImpl
import groovy.json.*

class BeforeBuildSaveTest extends Specification {

    def 'before build save test'() {
        setup:

        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = create(baseurl, 'admin', 'password') D
        def auth = "Basic ${'admin:password'.bytes.encodeBase64()}"

        when:

        def conn = new URL("${baseurl}/api/build").openConnection()
        conn.requestMethod = 'PUT'
        conn.doOutput = true
        conn.setRequestProperty('Authorization', auth)
        conn.setRequestProperty('Content-Type', 'application/json')
        def jsonfile = new File('./src/test/groovy/BeforeBuildSaveTest/build.json')
        jsonfile.withInputStream {conn.outputStream << it}
        conn.disconnect()

        ArtifactoryRequest request = new ArtifactoryRequestImpl().apiUrl("api/build/my-project/1")
                .method(ArtifactoryRequest.Method.GET)
                .responseType(ArtifactoryRequest.ContentType.JSON)
        def response = artifactory.restCall(request)

        then:

        assert response.buildInfo.modules.artifacts[0].type[0] == "war"

    }

}


