import spock.lang.Specification
import groovy.json.JsonSlurper

import org.jfrog.artifactory.client.ArtifactoryRequest
import org.jfrog.artifactory.client.impl.ArtifactoryRequestImpl
import org.jfrog.artifactory.client.ArtifactoryClientBuilder

class StorageSummaryTest extends Specification {
    def 'storage summary test'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = ArtifactoryClientBuilder.create().setUrl(baseurl)
            .setUsername('admin').setPassword('password').build()

        when:
        def pluginpath = "api/plugins/execute/storageSummary"
        def pluginreq = new ArtifactoryRequestImpl().apiUrl(pluginpath)
        pluginreq.method(ArtifactoryRequest.Method.GET)
        pluginreq.responseType(ArtifactoryRequest.ContentType.TEXT)
        def pluginstream = artifactory.restCall(pluginreq).getRawBody()
        def pluginout = new JsonSlurper().parseText(pluginstream)
        def apireq = new ArtifactoryRequestImpl().apiUrl("api/storageinfo")
        apireq.method(ArtifactoryRequest.Method.GET)
        apireq.responseType(ArtifactoryRequest.ContentType.JSON)
        def apiout = new groovy.json.JsonSlurper().parseText( artifactory.restCall(apireq).getRawBody())

        then:
        pluginout == apiout.storageSummary
    }
}
