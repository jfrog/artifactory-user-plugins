import groovy.json.JsonBuilder
import groovyx.net.http.HttpResponseException
import spock.lang.Specification

import static org.jfrog.artifactory.client.ArtifactoryClient.create

class ProtectPropertiesEditTest extends Specification {
    def 'property edit protect test'() {
        setup:
        def baseurl = "http://localhost:8088/artifactory"
        def auth = "Basic ${'admin:password'.bytes.encodeBase64().toString()}"
        def artifactory = create(baseurl, "admin", "password")
        def artuser = create(baseurl, "newuser", "password")
        def repo = artifactory.repository('libs-release-local')
        def conn = new URL(baseurl + '/api/security/users/newuser').openConnection()
        def json = new JsonBuilder()
        json name: 'newuser', email: 'newuser@jfrog.com', password: "password", admin: true
        conn.setDoOutput(true)
        conn.setRequestMethod('PUT')
        conn.setRequestProperty('Authorization', auth)
        conn.getOutputStream().write(json.toString().bytes)
        conn.getResponseCode()
        def tmpfile = new ByteArrayInputStream('temporary file'.bytes)
        repo.upload('tmpfile', tmpfile).doUpload()

        when:
        def file1 = artifactory.repository('libs-release-local').file('tmpfile')
        file1.properties().addProperty('prop1', 'value').doSet()
        file1.deleteProperty('prop1')

        then:
        notThrown(HttpResponseException)

        when:
        def file2 = artuser.repository('libs-release-local').file('tmpfile')
        file2.properties().addProperty('prop2', 'value').doSet()
        file2.deleteProperty('prop2')

        then:
        thrown(HttpResponseException)

        cleanup:
        repo.delete('tmpfile')
        conn = new URL(baseurl + '/api/security/users/newuser').openConnection()
        conn.setRequestMethod('DELETE')
        conn.setRequestProperty('Authorization', auth)
        conn.getResponseCode()
    }
}
