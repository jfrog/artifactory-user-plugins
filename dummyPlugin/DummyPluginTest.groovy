import groovy.json.JsonSlurper
import spock.lang.Specification

import static org.jfrog.artifactory.client.ArtifactoryClient.create

class DummyPluginTest extends Specification {
    def 'simple dummy plugin test'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = create(baseurl, 'admin', 'password')

        when:
        def json = new JsonSlurper().parseText(artifactory.plugins().execute('dummyPlugin').sync())

        then:
        json.status == 'okay'
    }
}
