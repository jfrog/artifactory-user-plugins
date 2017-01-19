import spock.lang.Specification
import groovy.json.JsonSlurper

import org.jfrog.artifactory.client.ArtifactoryRequest
import org.jfrog.artifactory.client.impl.ArtifactoryRequestImpl
import static org.jfrog.artifactory.client.ArtifactoryClient.create

class RequestRoutingTest extends Specification {
    def 'request routing test'() {
        when:
        def baseurlx = "http://localhost:$portx/artifactory"
        def artx = create(baseurlx, 'admin', 'password')
        def baseurly = "http://localhost:$porty/artifactory"
        def arty = create(baseurly, 'admin', 'password')
        def pluginpath = "api/plugins/execute/getVersion"
        def pluginreq = new ArtifactoryRequestImpl().apiUrl(pluginpath)
        pluginreq.addQueryParam("params", "serverId=$id")
        pluginreq.method(ArtifactoryRequest.Method.GET)
        pluginreq.responseType(ArtifactoryRequest.ContentType.TEXT)
        def pluginstream = artx.restCall(pluginreq)
        def pluginout = new JsonSlurper().parseText(pluginstream)
        def apireq = new ArtifactoryRequestImpl().apiUrl("api/system/version")
        apireq.method(ArtifactoryRequest.Method.GET)
        apireq.responseType(ArtifactoryRequest.ContentType.JSON)
        def apiout = arty.restCall(apireq)

        then:
        pluginout == apiout

        where:
        id    << ["art2", "art1"]
        portx << ["8088", "8081"]
        porty << ["8081", "8088"]
    }
}
