import groovy.json.JsonBuilder
import spock.lang.Specification

import java.text.SimpleDateFormat

import org.jfrog.artifactory.client.ArtifactoryClientBuilder

class OldBuildCleanupTest extends Specification {
    def 'old build cleanup test'() {
        setup:
        def auth = "Basic ${'admin:password'.bytes.encodeBase64().toString()}"
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = ArtifactoryClientBuilder.create().setUrl(baseurl)
            .setUsername('admin').setPassword('password').build()
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
        def cleanup = artifactory.plugins().execute('cleanOldBuilds')
        cleanup.withParameter('buildName', 'testbuild')
        cleanup.withParameter('buildNumber', '0')
        cleanup.withParameter('cleanArtifacts', 'true')
        cleanup.sync()
        conn = new URL(baseurl + '/api/build/testbuild/0').openConnection()
        conn.setRequestProperty('Authorization', auth)
        def code2 = conn.getResponseCode()

        then:
        code2 == 404
    }
}
