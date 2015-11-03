import groovy.json.JsonBuilder
import spock.lang.Specification

import java.text.SimpleDateFormat

import static org.jfrog.artifactory.client.ArtifactoryClient.create

class BuildCleanupTest extends Specification {
    def 'build cleanup test'() {
        setup:
        def auth = "Basic ${'admin:password'.bytes.encodeBase64().toString()}"
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = create(baseurl, 'admin', 'password')
        def date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
        def json = new JsonBuilder()
        json name: 'testbuild', number: '0', started: date.format(new Date())
        def conn = new URL(baseurl + '/api/build').openConnection()
        conn.setDoOutput(true)
        conn.setRequestMethod('PUT')
        conn.setRequestProperty('Content-Type', 'application/json')
        conn.setRequestProperty('Authorization', auth)
        conn.getOutputStream().write(json.toString().bytes)
        conn.getResponseCode()

        when:
        artifactory.plugins().execute('cleanBuilds').sync()
        conn = new URL(baseurl + '/api/build/testbuild/0').openConnection()
        conn.setRequestProperty('Authorization', auth)
        def code1 = conn.getResponseCode()

        then:
        code1 >= 200 && code1 < 300

        when:
        artifactory.plugins().execute('cleanBuilds').withParameter('days', '0').sync()
        conn = new URL(baseurl + '/api/build/testbuild/0').openConnection()
        conn.setRequestProperty('Authorization', auth)
        def code2 = conn.getResponseCode()

        then:
        code2 == 404
    }
}
