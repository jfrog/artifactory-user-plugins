import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import spock.lang.Specification

import static org.jfrog.artifactory.client.ArtifactoryClient.create

class BlackDuckConfigTest extends Specification {
    def 'black duck plugin test'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory/api/plugins/execute'
        def auth = "Basic ${'admin:password'.bytes.encodeBase64()}"
        def conn = new URL("$baseurl/getBlackDuck").openConnection()
        conn.setRequestProperty('Authorization', auth)
        assert conn.responseCode == 200
        def backup = conn.inputStream.bytes
        conn.disconnect()

        when:
        def json1 = [
            enableIntegration: true, serverUri: 'http://somehost',
            username: 'admin', password: 'password',
            connectionTimeoutMillis: 20000, proxy: null]
        conn = new URL("$baseurl/setBlackDuck").openConnection()
        conn.doOutput = true
        conn.requestMethod = 'POST'
        conn.setRequestProperty('Authorization', auth)
        conn.setRequestProperty('Content-Type', 'application/json')
        conn.outputStream.write(new JsonBuilder(json1).toString().bytes)
        assert conn.responseCode == 200
        conn.disconnect()
        conn = new URL("$baseurl/getBlackDuck").openConnection()
        conn.setRequestProperty('Authorization', auth)
        assert conn.responseCode == 200
        def reader1r = new InputStreamReader(conn.inputStream)
        def json1r = new JsonSlurper().parse(reader1r)
        conn.disconnect()

        then:
        json1 == json1r

        when:
        def json2 = [
            enableIntegration: true, serverUri: 'http://someotherhost',
            username: 'admin', password: 'password',
            connectionTimeoutMillis: 40000, proxy: null]
        conn = new URL("$baseurl/setBlackDuck").openConnection()
        conn.doOutput = true
        conn.requestMethod = 'POST'
        conn.setRequestProperty('Authorization', auth)
        conn.setRequestProperty('Content-Type', 'application/json')
        conn.outputStream.write(new JsonBuilder(json2).toString().bytes)
        assert conn.responseCode == 200
        conn.disconnect()
        conn = new URL("$baseurl/getBlackDuck").openConnection()
        conn.setRequestProperty('Authorization', auth)
        assert conn.responseCode == 200
        def reader2r = new InputStreamReader(conn.inputStream)
        def json2r = new JsonSlurper().parse(reader2r)
        conn.disconnect()

        then:
        json2 == json2r

        cleanup:
        conn = new URL("$baseurl/setBlackDuck").openConnection()
        conn.doOutput = true
        conn.requestMethod = 'POST'
        conn.setRequestProperty('Authorization', auth)
        conn.setRequestProperty('Content-Type', 'application/json')
        conn.outputStream.write(backup)
        conn.disconnect()
    }
}
