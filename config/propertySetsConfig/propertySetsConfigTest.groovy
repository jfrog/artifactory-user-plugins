import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import spock.lang.Specification

import static org.jfrog.artifactory.client.ArtifactoryClient.create

class PropertySetsConfigTest extends Specification {
    def 'property sets plugin test'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory/api/plugins/execute'
        def params = '?params=name=newpropset'
        def auth = "Basic ${'admin:password'.bytes.encodeBase64()}"
        def conn = null

        when:
        // get an initial list of property sets
        conn = new URL("$baseurl/getPropertySetsList").openConnection()
        conn.setRequestProperty('Authorization', auth)
        assert conn.responseCode == 200
        def listreader1 = new InputStreamReader(conn.inputStream)
        def jsonlist1 = new JsonSlurper().parse(listreader1)
        jsonlist1.sort()
        conn.disconnect()
        // add a new property set to the list
        def defvals = []
        defvals << [
            value: 'yes',
            defaultValue: true]
        defvals << [
            value: 'no',
            defaultValue: false]
        def props = []
        props << [
            name: 'newprop1',
            predefinedValues: [],
            propertyType: 'ANY_VALUE']
        props << [
            name: 'newprop2',
            predefinedValues: defvals,
            propertyType: 'SINGLE_SELECT']
        def json1 = [
            name: 'newpropset',
            properties: props]
        conn = new URL("$baseurl/addPropertySet").openConnection()
        conn.doOutput = true
        conn.setRequestProperty('Authorization', auth)
        conn.setRequestProperty('Content-Type', 'application/json')
        conn.outputStream.write(new JsonBuilder(json1).toString().bytes)
        assert conn.responseCode == 200
        conn.disconnect()
        // get a new property set list, containing the new property set
        conn = new URL("$baseurl/getPropertySetsList").openConnection()
        conn.setRequestProperty('Authorization', auth)
        assert conn.responseCode == 200
        def listreader2 = new InputStreamReader(conn.inputStream)
        def jsonlist2 = new JsonSlurper().parse(listreader2)
        jsonlist2.sort()
        conn.disconnect()
        // get the new property set data
        conn = new URL("$baseurl/getPropertySet$params").openConnection()
        conn.setRequestProperty('Authorization', auth)
        assert conn.responseCode == 200
        def reader1r = new InputStreamReader(conn.inputStream)
        def json1r = new JsonSlurper().parse(reader1r)
        conn.disconnect()
        // update the new property set
        def json2diff = [
            properties: [json1['properties'][0]]]
        def json2 = json1 + json2diff
        conn = new URL("$baseurl/updatePropertySet$params").openConnection()
        conn.doOutput = true
        conn.requestMethod = 'POST'
        conn.setRequestProperty('Authorization', auth)
        conn.setRequestProperty('Content-Type', 'application/json')
        conn.outputStream.write(new JsonBuilder(json2diff).toString().bytes)
        assert conn.responseCode == 200
        conn.disconnect()
        // get the property set list again, still containing the new set
        conn = new URL("$baseurl/getPropertySetsList").openConnection()
        conn.setRequestProperty('Authorization', auth)
        assert conn.responseCode == 200
        def listreader2r = new InputStreamReader(conn.inputStream)
        def jsonlist2r = new JsonSlurper().parse(listreader2r)
        jsonlist2r.sort()
        conn.disconnect()
        // get the modified property set data
        conn = new URL("$baseurl/getPropertySet$params").openConnection()
        conn.setRequestProperty('Authorization', auth)
        assert conn.responseCode == 200
        def reader2r = new InputStreamReader(conn.inputStream)
        def json2r = new JsonSlurper().parse(reader2r)
        conn.disconnect()
        // delete the new property set
        conn = new URL("$baseurl/deletePropertySet$params").openConnection()
        conn.requestMethod = 'DELETE'
        conn.setRequestProperty('Authorization', auth)
        assert conn.responseCode == 200
        conn.disconnect()
        // get the property set list again, not containing the new property set
        conn = new URL("$baseurl/getPropertySetsList").openConnection()
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
        !('newpropset' in jsonlist1)
        'newpropset' in jsonlist2
    }
}
