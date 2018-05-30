import spock.lang.Specification
import groovy.json.JsonSlurper
import org.jfrog.artifactory.client.ArtifactoryClientBuilder
import org.jfrog.artifactory.client.ArtifactoryRequest
import org.jfrog.artifactory.client.impl.ArtifactoryRequestImpl
import org.jfrog.lilypad.Control

class InactiveServerCleanerTest extends Specification {
    def 'cleanup inactive server test'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = ArtifactoryClientBuilder.create().setUrl(baseurl)
            .setUsername('admin').setPassword('password').build()

        when:
        def serversInfo = getHAServersInfo(artifactory);

        then:
        serversInfo.members.size == 2
        serversInfo.members[0].serverState == "RUNNING"
        serversInfo.members[1].serverState == "RUNNING"

        when:
        Control.stopNode('8082', '8081')
        sleep(60000)
        requestCleanup(artifactory)
        serversInfo = getHAServersInfo(artifactory);

        then:
        serversInfo.members.size == 1
        serversInfo.members[0].serverState == "RUNNING"
    }

    def getHAServersInfo(artifactory) {
        def pluginpath = "api/plugins/execute/haClusterDump"
        def pluginreq = new ArtifactoryRequestImpl().apiUrl(pluginpath)
        pluginreq.method(ArtifactoryRequest.Method.GET)
        pluginreq.responseType(ArtifactoryRequest.ContentType.TEXT)
        def pluginstream = artifactory.restCall(pluginreq).getRawBody()
        return new JsonSlurper().parseText(pluginstream)
    }

    def requestCleanup(artifactory) {
        def pluginpath = "api/plugins/execute/cleanHAInactiveServers"
        def pluginreq = new ArtifactoryRequestImpl().apiUrl(pluginpath)
        pluginreq.method(ArtifactoryRequest.Method.POST)
        artifactory.restCall(pluginreq)
    }
}
