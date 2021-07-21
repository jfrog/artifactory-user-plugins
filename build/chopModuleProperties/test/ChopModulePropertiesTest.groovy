import spock.lang.Specification

import org.jfrog.artifactory.client.ArtifactoryClientBuilder
import org.apache.http.client.HttpResponseException
import org.jfrog.artifactory.client.ArtifactoryRequest
import org.jfrog.artifactory.client.impl.ArtifactoryRequestImpl

import groovy.json.JsonSlurper

class ChopModulePropertiesTest extends Specification {
    def 'ChopModuleProperties Test' () {

        setup: 

        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = ArtifactoryClientBuilder.create().setUrl(baseurl)
            .setUsername('admin').setPassword('password').build()

        def file = new File("./src/test/groovy/ChopModulePropertiesTest/build.json")
        def request = new JsonSlurper().parse(file)

        ArtifactoryRequest uploadBuild = new ArtifactoryRequestImpl().apiUrl("api/build")
        .method(ArtifactoryRequest.Method.PUT)
        .requestType(ArtifactoryRequest.ContentType.JSON)
        .requestBody(new JsonSlurper().parse(file))

        artifactory.restCall(uploadBuild)


        when: 

        ArtifactoryRequest getBuildInfo = new ArtifactoryRequestImpl().apiUrl("api/build/test-build/1")
        .method(ArtifactoryRequest.Method.GET)
        .responseType(ArtifactoryRequest.ContentType.JSON)

        def response = new groovy.json.JsonSlurper().parseText( artifactory.restCall(getBuildInfo).getRawBody())


        then:
        response.buildInfo.modules.every {it.properties.every {it.value.size() < 900}}

        cleanup:

        ArtifactoryRequest delete = new ArtifactoryRequestImpl().apiUrl("api/build/test-build")
        .setQueryParams(deleteAll: "1")
        .method(ArtifactoryRequest.Method.DELETE)
        artifactory.restCall(delete)

    }
}
