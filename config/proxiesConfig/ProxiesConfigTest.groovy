import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import spock.lang.Specification

import static org.jfrog.artifactory.client.ArtifactoryClient.create

class ProxiesConfigTest extends Specification {
    def 'proxies plugin test'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory/api/plugins/execute'
        def params = '?params=key=newproxy'
        def auth = "Basic ${'admin:password'.bytes.encodeBase64()}"
        def conn = null

        when:
        // get an initial list of proxies
        conn = new URL("$baseurl/getProxiesList").openConnection()
        conn.setRequestProperty('Authorization', auth)
        assert conn.responseCode == 200
        def listreader1 = new InputStreamReader(conn.inputStream)
        def jsonlist1 = new JsonSlurper().parse(listreader1)
        jsonlist1.sort()
        conn.disconnect()
        // add a new proxy to the list
        def json1 = [
            key: 'newproxy',
            host: 'localhost', port: 8765,
            username: null, password: null,
            ntHost: null, domain: null,
            defaultProxy: false,
            redirectedToHosts: 'rehost']
        conn = new URL("$baseurl/addProxy").openConnection()
        conn.doOutput = true
        conn.setRequestProperty('Authorization', auth)
        conn.setRequestProperty('Content-Type', 'application/json')
        conn.outputStream.write(new JsonBuilder(json1).toString().bytes)
        assert conn.responseCode == 200
        conn.disconnect()
        // get a new proxy list, containing the new proxy
        conn = new URL("$baseurl/getProxiesList").openConnection()
        conn.setRequestProperty('Authorization', auth)
        assert conn.responseCode == 200
        def listreader2 = new InputStreamReader(conn.inputStream)
        def jsonlist2 = new JsonSlurper().parse(listreader2)
        jsonlist2.sort()
        conn.disconnect()
        // get the new proxy data
        conn = new URL("$baseurl/getProxy$params").openConnection()
        conn.setRequestProperty('Authorization', auth)
        assert conn.responseCode == 200
        def reader1r = new InputStreamReader(conn.inputStream)
        def json1r = new JsonSlurper().parse(reader1r)
        conn.disconnect()
        // update the new proxy
        def json2diff = [host: 'localhost', port: 8901, defaultProxy: true]
        def json2 = json1 + json2diff
        conn = new URL("$baseurl/updateProxy$params").openConnection()
        conn.doOutput = true
        conn.requestMethod = 'POST'
        conn.setRequestProperty('Authorization', auth)
        conn.setRequestProperty('Content-Type', 'application/json')
        conn.outputStream.write(new JsonBuilder(json2diff).toString().bytes)
        assert conn.responseCode == 200
        conn.disconnect()
        // get the proxy list again, still containing the new proxy
        conn = new URL("$baseurl/getProxiesList").openConnection()
        conn.setRequestProperty('Authorization', auth)
        assert conn.responseCode == 200
        def listreader2r = new InputStreamReader(conn.inputStream)
        def jsonlist2r = new JsonSlurper().parse(listreader2r)
        jsonlist2r.sort()
        conn.disconnect()
        // get the modified proxy data
        conn = new URL("$baseurl/getProxy$params").openConnection()
        conn.setRequestProperty('Authorization', auth)
        assert conn.responseCode == 200
        def reader2r = new InputStreamReader(conn.inputStream)
        def json2r = new JsonSlurper().parse(reader2r)
        conn.disconnect()
        // delete the new proxy
        conn = new URL("$baseurl/deleteProxy$params").openConnection()
        conn.requestMethod = 'DELETE'
        conn.setRequestProperty('Authorization', auth)
        assert conn.responseCode == 200
        conn.disconnect()
        // get the proxy list again, not containing the new proxy
        conn = new URL("$baseurl/getProxiesList").openConnection()
        conn.setRequestProperty('Authorization', auth)
        assert conn.responseCode == 200
        def listreader1r = new InputStreamReader(conn.inputStream)
        def jsonlist1r = new JsonSlurper().parse(listreader1r)
        jsonlist1r.sort()
        conn.disconnect()

        then:
        json1 == json1r
        json2 == json2r
        jsonlist1 == jsonlist1r
        jsonlist2 == jsonlist2r
        !('newproxy' in jsonlist1)
        'newproxy' in jsonlist2
    }
}
