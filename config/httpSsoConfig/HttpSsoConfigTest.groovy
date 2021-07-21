import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import spock.lang.Specification

class HttpSsoConfigTest extends Specification {
    def 'http sso plugin test'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory/api/plugins/execute'
        def auth = "Basic ${'admin:password'.bytes.encodeBase64()}"
        def conn = new URL("$baseurl/getHttpSso").openConnection()
        conn.setRequestProperty('Authorization', auth)
        assert conn.responseCode == 200
        def backup = conn.inputStream.bytes
        conn.disconnect()

        when:
        def json1 = [
            httpSsoProxied: false,
            noAutoUserCreation: true,
            allowUserToAccessProfile: true,
            remoteUserRequestVariable: 'REMOTE_USER']
        conn = new URL("$baseurl/setHttpSso").openConnection()
        conn.doOutput = true
        conn.requestMethod = 'POST'
        conn.setRequestProperty('Authorization', auth)
        conn.setRequestProperty('Content-Type', 'application/json')
        conn.outputStream.write(new JsonBuilder(json1).toString().bytes)
        assert conn.responseCode == 200
        conn.disconnect()
        conn = new URL("$baseurl/getHttpSso").openConnection()
        conn.setRequestProperty('Authorization', auth)
        assert conn.responseCode == 200
        def reader1r = new InputStreamReader(conn.inputStream)
        def json1r = new JsonSlurper().parse(reader1r)
        conn.disconnect()

        then:
        json1 == json1r

        when:
        def json2 = [
            httpSsoProxied: true,
            noAutoUserCreation: false,
            allowUserToAccessProfile: false,
            remoteUserRequestVariable: 'LOCAL_USER']
        conn = new URL("$baseurl/setHttpSso").openConnection()
        conn.doOutput = true
        conn.requestMethod = 'POST'
        conn.setRequestProperty('Authorization', auth)
        conn.setRequestProperty('Content-Type', 'application/json')
        conn.outputStream.write(new JsonBuilder(json2).toString().bytes)
        assert conn.responseCode == 200
        conn.disconnect()
        conn = new URL("$baseurl/getHttpSso").openConnection()
        conn.setRequestProperty('Authorization', auth)
        assert conn.responseCode == 200
        def reader2r = new InputStreamReader(conn.inputStream)
        def json2r = new JsonSlurper().parse(reader2r)
        conn.disconnect()

        then:
        json2 == json2r

        cleanup:
        conn = new URL("$baseurl/setHttpSso").openConnection()
        conn.doOutput = true
        conn.requestMethod = 'POST'
        conn.setRequestProperty('Authorization', auth)
        conn.setRequestProperty('Content-Type', 'application/json')
        conn.outputStream.write(backup)
        conn.disconnect()
    }
}
