import spock.lang.Specification

import org.jfrog.artifactory.client.ArtifactoryRequest
import org.jfrog.artifactory.client.impl.ArtifactoryRequestImpl
//import static org.jfrog.artifactory.client.ArtifactoryClient.create
import org.joda.time.DateTime;
import org.joda.time.format.ISODateTimeFormat;

class StorageSummaryTest extends Specification {
    def 'storage summary test'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = create(baseurl, 'admin', 'password')

        when:
        def pluginpath = "api/plugins/execute/currentDate"
        def pluginreq = new ArtifactoryRequestImpl().apiUrl(pluginpath)
        pluginreq.method(ArtifactoryRequest.Method.GET)
        pluginreq.responseType(ArtifactoryRequest.ContentType.TEXT)
        def pluginstream = artifactory.restCall(pluginreq)
        currentYear=date[Calendar.YEAR]
        currentMonth=date[Calendar.MONTH]
        
        then: //TODO How do I confirm that this works?
        input=new DateTime(pluginstream)
        input.getYear()==currentYear
        input.getMonth()==currentMonth
    }
}
