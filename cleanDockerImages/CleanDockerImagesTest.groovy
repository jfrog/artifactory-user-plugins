/**
 * Created by madhur on 6/16/17.
 */

import groovy.json.JsonSlurper
import spock.lang.Specification

import static org.jfrog.artifactory.client.ArtifactoryClient.create

class CleanDockerImagesTest extends Specification {
    def 'simple clean docker images plugin test'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = create(baseurl, 'admin', 'password')

        when:
        def json = new JsonSlurper().parseText(artifactory.plugins().execute('cleanDockerImages').sync())

        then:
        json.status == 'okay'

    }
}
