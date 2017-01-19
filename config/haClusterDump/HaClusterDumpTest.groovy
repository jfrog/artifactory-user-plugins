import spock.lang.Specification
import groovy.json.JsonSlurper

import org.jfrog.artifactory.client.ArtifactoryRequest
import org.jfrog.artifactory.client.impl.ArtifactoryRequestImpl
import static org.jfrog.artifactory.client.ArtifactoryClient.create

class HaClusterDumpTest extends Specification {
    def 'cluster dump test'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = create(baseurl, 'admin', 'password')

        when:
        def pluginmembers = []
        def plugindata = [:]
        def integmembers = []
        def integdata = [:]

        // get plugin data
        def pluginpath = "api/plugins/execute/haClusterDump"
        def pluginreq = new ArtifactoryRequestImpl().apiUrl(pluginpath)
        pluginreq.method(ArtifactoryRequest.Method.GET)
        pluginreq.responseType(ArtifactoryRequest.ContentType.TEXT)
        def pluginstream = artifactory.restCall(pluginreq)
        def pluginout = new JsonSlurper().parseText(pluginstream)
        for (member in pluginout.members) {
            pluginmembers << '/' + member.address
            if (member.localMember) {
                plugindata.port = member.address - ~'^.*:'
                plugindata.id = member.serverId
                plugindata.primary = member.serverRole == 'Primary'
                plugindata.url = member.contextUrl
            }
        }

        // get integrated data
        def integpath = "api/ha-admin/clusterDump"
        def integreq = new ArtifactoryRequestImpl().apiUrl(integpath)
        integreq.method(ArtifactoryRequest.Method.GET)
        integreq.responseType(ArtifactoryRequest.ContentType.TEXT)
        def integstream = artifactory.restCall(integreq)

        // parse integrated data
        def regex = '^\t([^=]*)(?:=(.*))?$'
        def currsection = null
        def rawdata = [:]
        integstream.eachLine {
            if (it[0] != '\t') {
                currsection = it
                rawdata[it] = [:]
            } else {
                def match = it =~ regex
                rawdata[currsection][match[0][1]] = match[0][2]
            }
        }
        rawdata.members.each { id, nil -> integmembers << id }
        integdata.port = rawdata.properties['membership.port']
        integdata.id = rawdata.properties['node.id']
        integdata.primary = rawdata.properties.primary == 'true'
        integdata.url = rawdata.properties['context.url']

        then:
        rawdata.heartbeats.keySet() == rawdata.members.keySet()
        pluginmembers == integmembers
        plugindata == integdata
    }
}
