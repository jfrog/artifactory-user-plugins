import org.jfrog.artifactory.client.ArtifactoryRequest
import org.jfrog.artifactory.client.impl.ArtifactoryRequestImpl
import spock.lang.Specification
import groovy.json.*

import org.jfrog.artifactory.client.ArtifactoryClientBuilder

class BeforeBuildSaveTest extends Specification {

    def 'before build save test'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = ArtifactoryClientBuilder.create().setUrl(baseurl)
            .setUsername('admin').setPassword('password').build()
        def auth = "Basic ${'admin:password'.bytes.encodeBase64()}"

        def file = new File('./src/test/groovy/BeforeBuildSaveTest/build.json')

        ArtifactoryRequest uploadBuild = new ArtifactoryRequestImpl().apiUrl("api/build")
                .method(ArtifactoryRequest.Method.PUT)
                .requestType(ArtifactoryRequest.ContentType.JSON)
                .requestBody(new JsonSlurper().parse(file))
        artifactory.restCall(uploadBuild)

        when:
        ArtifactoryRequest request = new ArtifactoryRequestImpl().apiUrl("api/build/test-build/1")
                .method(ArtifactoryRequest.Method.GET)
                .responseType(ArtifactoryRequest.ContentType.JSON)
        def response = new groovy.json.JsonSlurper().parseText( artifactory.restCall(request).getRawBody())

        then:
        response.buildInfo.modules.artifacts[0].type[0] == "war"

        cleanup:
        ArtifactoryRequest delete = new ArtifactoryRequestImpl().apiUrl("api/build/test-build")
                .setQueryParams(deleteAll: "1")
                .method(ArtifactoryRequest.Method.DELETE)
        artifactory.restCall(delete)
    }
}
