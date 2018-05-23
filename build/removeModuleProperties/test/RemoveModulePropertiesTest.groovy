import spock.lang.Specification
import org.jfrog.artifactory.client.ArtifactoryClientBuilder
import org.jfrog.artifactory.client.ArtifactoryRequest
import org.jfrog.artifactory.client.impl.ArtifactoryRequestImpl
import groovy.json.*


class RemoveModulePropertiesTest extends Specification {
    def 'removeModulePropertiesTest'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = ArtifactoryClientBuilder.create().setUrl(baseurl)
            .setUsername('admin').setPassword('password').build()

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
        def response = new groovy.json.JsonSlurper().parseText( artifactory.restCall(request).getRawBody())

        then:
        response.buildInfo.modules.every { it.properties == [:] }

        cleanup:
        ArtifactoryRequest delete = new ArtifactoryRequestImpl().apiUrl("api/build/stm-test")
          .setQueryParams(deleteAll: "1")
          .method(ArtifactoryRequest.Method.DELETE)
        artifactory.restCall(delete)
    }
}
