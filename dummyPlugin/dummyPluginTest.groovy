import groovy.json.JsonSlurper

import static org.jfrog.artifactory.client.ArtifactoryClient.create
import spock.lang.Specification

class DummyPluginTest extends Specification {
    def 'simple dummy plugin test'() {
        setup:
        def artifactory = create("http://localhost:8088/artifactory", "admin", "password")

        when:
        def json = new JsonSlurper().parseText(artifactory.plugins().execute('dummyPlugin').sync())

        then:
        json.status == 'okay'
    }
}
