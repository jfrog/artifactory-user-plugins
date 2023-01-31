import groovy.json.JsonSlurper
import spock.lang.Specification

import org.jfrog.artifactory.client.ArtifactoryClientBuilder

class DummyPluginTest extends Specification {
    def 'simple dummy plugin test'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = ArtifactoryClientBuilder.create().setUrl(baseurl)
            .setUsername('admin').setPassword('password').build()

        when:
        def json = new JsonSlurper().parseText(artifactory.plugins().execute('dummyPlugin').sync())

        then:
        json.status == 'okay'
    }
}
