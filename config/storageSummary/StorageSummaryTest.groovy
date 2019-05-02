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
        def pluginreq1 = new ArtifactoryRequestImpl().apiUrl(pluginpath)
        pluginreq1.method(ArtifactoryRequest.Method.GET)
        pluginreq1.responseType(ArtifactoryRequest.ContentType.TEXT)
        def pluginstream1 = artifactory.restCall(pluginreq1).getRawBody()
        def pluginout1 = new JsonSlurper().parseText(pluginstream1)
        def apireq = new ArtifactoryRequestImpl().apiUrl("api/storageinfo")
        apireq.method(ArtifactoryRequest.Method.GET)
        apireq.responseType(ArtifactoryRequest.ContentType.JSON)
        def apiout = new groovy.json.JsonSlurper().parseText( artifactory.restCall(apireq).getRawBody())
        def pluginreq2 = new ArtifactoryRequestImpl().apiUrl(pluginpath)
        pluginreq2.method(ArtifactoryRequest.Method.GET)
        pluginreq2.responseType(ArtifactoryRequest.ContentType.TEXT)
        def pluginstream2 = artifactory.restCall(pluginreq2).getRawBody()
        def pluginout2 = new JsonSlurper().parseText(pluginstream2)

        then:
        pluginout1 == apiout.storageSummary || pluginout2 == apiout.storageSummary
    }
}
