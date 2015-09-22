import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import spock.lang.Specification

import static org.jfrog.artifactory.client.ArtifactoryClient.create

class SamlConfigTest extends Specification {
    def 'saml plugin test'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory/api/plugins/execute'
        def auth = "Basic ${'admin:password'.bytes.encodeBase64()}"
        def conn = new URL("$baseurl/getSaml").openConnection()
        conn.setRequestProperty('Authorization', auth)
        assert conn.responseCode == 200
        def backup = conn.inputStream.bytes

        when:
        def json1 = [
            enableIntegration: true,
            loginUrl: 'http://foobarlogin', logoutUrl: 'http://foobarlogout',
            serviceProviderName: 'foobarserviceprovidername',
            noAutoUserCreation: false,
            certificate: 'foobarcertificate']
        conn = new URL("$baseurl/setSaml").openConnection()
        conn.doOutput = true
        conn.requestMethod = 'POST'
        conn.setRequestProperty('Authorization', auth)
        conn.setRequestProperty('Content-Type', 'application/json')
        conn.outputStream.write(new JsonBuilder(json1).toString().bytes)
        assert conn.responseCode == 200
        conn = new URL("$baseurl/getSaml").openConnection()
        conn.setRequestProperty('Authorization', auth)
        assert conn.responseCode == 200
        def json1r = new JsonSlurper().parse(conn.inputStream)

        then:
        json1 == json1r

        when:
        def json2 = [
            enableIntegration: false,
            loginUrl: 'http://barbazlogin', logoutUrl: 'http://barbazlogout',
            serviceProviderName: 'barbazserviceprovidername',
            noAutoUserCreation: true,
            certificate: 'barbazcertificate']
        conn = new URL("$baseurl/setSaml").openConnection()
        conn.doOutput = true
        conn.requestMethod = 'POST'
        conn.setRequestProperty('Authorization', auth)
        conn.setRequestProperty('Content-Type', 'application/json')
        conn.outputStream.write(new JsonBuilder(json2).toString().bytes)
        assert conn.responseCode == 200
        conn = new URL("$baseurl/getSaml").openConnection()
        conn.setRequestProperty('Authorization', auth)
        assert conn.responseCode == 200
        def json2r = new JsonSlurper().parse(conn.inputStream)

        then:
        json2 == json2r

        cleanup:
        conn = new URL("$baseurl/setSaml").openConnection()
        conn.doOutput = true
        conn.requestMethod = 'POST'
        conn.setRequestProperty('Authorization', auth)
        conn.setRequestProperty('Content-Type', 'application/json')
        conn.outputStream.write(backup)
        assert conn.responseCode == 200
    }
}
