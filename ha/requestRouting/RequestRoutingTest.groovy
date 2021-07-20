import spock.lang.Specification
import groovy.json.JsonSlurper

import org.jfrog.artifactory.client.ArtifactoryRequest
import org.jfrog.artifactory.client.impl.ArtifactoryRequestImpl
import org.jfrog.artifactory.client.ArtifactoryClientBuilder

class RequestRoutingTest extends Specification {
    def 'request routing test'() {
        when:
        def baseurlx = "http://localhost:$portx/artifactory"
        def artx = ArtifactoryClientBuilder.create().setUrl(baseurlx)
            .setUsername('admin').setPassword('password').build()
        def baseurly = "http://localhost:$porty/artifactory"
        def arty = ArtifactoryClientBuilder.create().setUrl(baseurly)
            .setUsername('admin').setPassword('password').build()
        def pluginpath = "api/plugins/execute/getVersion"
        def pluginreq = new ArtifactoryRequestImpl().apiUrl(pluginpath)
        pluginreq.addQueryParam("params", "serverId=$id")
        pluginreq.method(ArtifactoryRequest.Method.GET)
        pluginreq.responseType(ArtifactoryRequest.ContentType.TEXT)
        def pluginstream = artx.restCall(pluginreq).getRawBody()
        def pluginout = new JsonSlurper().parseText(pluginstream)
        def apireq = new ArtifactoryRequestImpl().apiUrl("api/system/version")
        apireq.method(ArtifactoryRequest.Method.GET)
        apireq.responseType(ArtifactoryRequest.ContentType.JSON)
        def apiout = new groovy.json.JsonSlurper().parseText( arty.restCall(apireq).getRawBody())

        then:
        pluginout == apiout

        where:
        id    << ["art2", "art1"]
        portx << ["8088", "8081"]
        porty << ["8081", "8088"]
    }
}
