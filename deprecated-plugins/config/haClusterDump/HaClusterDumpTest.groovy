import spock.lang.Specification
import groovy.json.JsonSlurper

import org.jfrog.artifactory.client.ArtifactoryRequest
import org.jfrog.artifactory.client.impl.ArtifactoryRequestImpl
import org.jfrog.artifactory.client.ArtifactoryClientBuilder

class HaClusterDumpTest extends Specification {
    def 'cluster dump test'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = ArtifactoryClientBuilder.create().setUrl(baseurl)
            .setUsername('admin').setPassword('password').build()

        when:
        def pluginmembers = [] as Set

        // get plugin data
        def pluginpath = "api/plugins/execute/haClusterDump"
        def pluginreq = new ArtifactoryRequestImpl().apiUrl(pluginpath)
        pluginreq.method(ArtifactoryRequest.Method.GET)
        pluginreq.responseType(ArtifactoryRequest.ContentType.TEXT)
        def pluginstream = artifactory.restCall(pluginreq).getRawBody()
        def pluginout = new JsonSlurper().parseText(pluginstream)

        then:
        pluginout.active == true
        pluginout.members.size() == 2
        validateMember(pluginout.members[0])
        validateMember(pluginout.members[1])
    }

    def validateMember(member) {
        member.serverId != null &&
        (member.localMember == true || member.localMember == false) &&
        member.address != null &&
        member.contextUrl != null &&
        member.heartbeat != null &&
        member.serverState == 'RUNNING' &&
        (member.serverRole == 'Primary' || member.serverRole == 'Member') &&
        member.artifactoryVersion != null &&
        member.licenseHash != null
    }
}
