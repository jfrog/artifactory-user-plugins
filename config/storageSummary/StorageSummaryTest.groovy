import spock.lang.Specification
import groovy.json.JsonSlurper

import org.jfrog.artifactory.client.ArtifactoryRequest
import org.jfrog.artifactory.client.impl.ArtifactoryRequestImpl
import static org.jfrog.artifactory.client.ArtifactoryClient.create

class StorageSummaryTest extends Specification {
    def 'storage summary test'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = create(baseurl, 'admin', 'password')

        when:
        def pluginpath = "api/plugins/execute/storageSummary"
        def pluginreq = new ArtifactoryRequestImpl().apiUrl(pluginpath)
        pluginreq.method(ArtifactoryRequest.Method.GET)
        pluginreq.responseType(ArtifactoryRequest.ContentType.TEXT)
        def pluginstream = artifactory.restCall(pluginreq)
        def pluginout = new JsonSlurper().parseText(pluginstream)
        def apireq = new ArtifactoryRequestImpl().apiUrl("api/storageinfo")
        apireq.method(ArtifactoryRequest.Method.GET)
        apireq.responseType(ArtifactoryRequest.ContentType.JSON)
        def apiout = artifactory.restCall(apireq)

        then:
        pluginout == apiout.storageSummary
    }
}
