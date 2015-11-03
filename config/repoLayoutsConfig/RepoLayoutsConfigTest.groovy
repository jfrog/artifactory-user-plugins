import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import spock.lang.Specification

import static org.jfrog.artifactory.client.ArtifactoryClient.create

class RepoLayoutsConfigTest extends Specification {
    def 'repo layouts plugin test'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory/api/plugins/execute'
        def params = '?params=name=newlayout'
        def auth = "Basic ${'admin:password'.bytes.encodeBase64()}"
        def conn = null

        when:
        // get an initial list of layouts
        conn = new URL("$baseurl/getLayoutsList").openConnection()
        conn.setRequestProperty('Authorization', auth)
        assert conn.responseCode == 200
        def listreader1 = new InputStreamReader(conn.inputStream)
        def jsonlist1 = new JsonSlurper().parse(listreader1)
        jsonlist1.sort()
        conn.disconnect()
        // add a new layout to the list
        def json1 = [
            name: 'newlayout',
            artifactPathPattern: '[org].[module].[baseRev].[ext]',
            distinctiveDescriptorPathPattern: false,
            descriptorPathPattern: null,
            folderIntegrationRevisionRegExp: '.*',
            fileIntegrationRevisionRegExp: '.*']
        conn = new URL("$baseurl/addLayout").openConnection()
        conn.doOutput = true
        conn.setRequestProperty('Authorization', auth)
        conn.setRequestProperty('Content-Type', 'application/json')
        conn.outputStream.write(new JsonBuilder(json1).toString().bytes)
        assert conn.responseCode == 200
        conn.disconnect()
        // get a new layout list, containing the new layout
        conn = new URL("$baseurl/getLayoutsList").openConnection()
        conn.setRequestProperty('Authorization', auth)
        assert conn.responseCode == 200
        def listreader2 = new InputStreamReader(conn.inputStream)
        def jsonlist2 = new JsonSlurper().parse(listreader2)
        jsonlist2.sort()
        conn.disconnect()
        // get the new layout data
        conn = new URL("$baseurl/getLayout$params").openConnection()
        conn.setRequestProperty('Authorization', auth)
        assert conn.responseCode == 200
        def reader1r = new InputStreamReader(conn.inputStream)
        def json1r = new JsonSlurper().parse(reader1r)
        conn.disconnect()
        // update the new layout
        def json2diff = [distinctiveDescriptorPathPattern: true,
                         descriptorPathPattern: '[org].[module].[baseRev].foo']
        def json2 = json1 + json2diff
        conn = new URL("$baseurl/updateLayout$params").openConnection()
        conn.doOutput = true
        conn.requestMethod = 'POST'
        conn.setRequestProperty('Authorization', auth)
        conn.setRequestProperty('Content-Type', 'application/json')
        conn.outputStream.write(new JsonBuilder(json2diff).toString().bytes)
        assert conn.responseCode == 200
        conn.disconnect()
        // get the layout list again, still containing the new layout
        conn = new URL("$baseurl/getLayoutsList").openConnection()
        conn.setRequestProperty('Authorization', auth)
        assert conn.responseCode == 200
        def listreader2r = new InputStreamReader(conn.inputStream)
        def jsonlist2r = new JsonSlurper().parse(listreader2r)
        jsonlist2r.sort()
        conn.disconnect()
        // get the modified layout data
        conn = new URL("$baseurl/getLayout$params").openConnection()
        conn.setRequestProperty('Authorization', auth)
        assert conn.responseCode == 200
        def reader2r = new InputStreamReader(conn.inputStream)
        def json2r = new JsonSlurper().parse(reader2r)
        conn.disconnect()
        // delete the new layout
        conn = new URL("$baseurl/deleteLayout$params").openConnection()
        conn.requestMethod = 'DELETE'
        conn.setRequestProperty('Authorization', auth)
        assert conn.responseCode == 200
        conn.disconnect()
        // get the layout list again, not containing the new layout
        conn = new URL("$baseurl/getLayoutsList").openConnection()
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
        !('newlayout' in jsonlist1)
        'newlayout' in jsonlist2
    }
}
