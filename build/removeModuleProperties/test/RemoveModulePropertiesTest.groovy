import spock.lang.Specification
import static org.jfrog.artifactory.client.ArtifactoryClient.create
import org.jfrog.artifactory.client.ArtifactoryRequest
import org.jfrog.artifactory.client.impl.ArtifactoryRequestImpl
import groovy.json.*


class RemoveModulePropertiesTest extends Specification {
    def 'removeModulePropertiesTest'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = create(baseurl, 'admin', 'password')

        def file = new File('./src/test/groovy/RemoveModulePropertiesTest/build.json')

        ArtifactoryRequest uploadBuild = new ArtifactoryRequestImpl().apiUrl("api/build")
          .method(ArtifactoryRequest.Method.PUT)
          .requestType(ArtifactoryRequest.ContentType.JSON)
          .requestBody(new JsonSlurper().parse(file))
        artifactory.restCall(uploadBuild)

        when:
        ArtifactoryRequest request = new ArtifactoryRequestImpl().apiUrl("api/build/stm-test/46")
          .method(ArtifactoryRequest.Method.GET)
          .responseType(ArtifactoryRequest.ContentType.JSON)
        def response = artifactory.restCall(request)

        then:
        response.buildInfo.modules.every { it.properties == [:] }

        cleanup:
        ArtifactoryRequest delete = new ArtifactoryRequestImpl().apiUrl("api/build/stm-test")
          .setQueryParams(deleteAll: 1)
          .method(ArtifactoryRequest.Method.DELETE)
        artifactory.restCall(delete)
    }
}
