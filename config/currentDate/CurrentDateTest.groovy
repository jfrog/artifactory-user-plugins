import spock.lang.Specification

import java.text.SimpleDateFormat
import org.jfrog.artifactory.client.ArtifactoryRequest
import org.jfrog.artifactory.client.impl.ArtifactoryRequestImpl
import static org.jfrog.artifactory.client.ArtifactoryClient.create

class CurrentDateTest extends Specification {
    def 'currentDate test'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = create(baseurl, 'admin', 'password')

        when:
        def pluginpath = "api/plugins/execute/currentDate"
        def pluginreq = new ArtifactoryRequestImpl().apiUrl(pluginpath)
        pluginreq.method(ArtifactoryRequest.Method.GET)
        pluginreq.responseType(ArtifactoryRequest.ContentType.TEXT)
        def pluginstream = artifactory.restCall(pluginreq)

        then:
        //confirm that the year is correct, the month is correct in expected format.
        def format = new SimpleDateFormat("yyyy-MM-")
        pluginstream.startsWith(format.format(new Date()))
    }
}
