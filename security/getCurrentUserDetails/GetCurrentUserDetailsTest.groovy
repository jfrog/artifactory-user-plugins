import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import spock.lang.Specification

class GetCurrentUserDetailsTest extends Specification {
    def 'get current user details test'() {
        setup:
        def baseurl = "http://localhost:8088/artifactory"
        def auth = "Basic ${'admin:password'.bytes.encodeBase64().toString()}"
        def userauth = "Basic ${'newuser:password'.bytes.encodeBase64().toString()}"
        def conn = new URL(baseurl + '/api/security/users/newuser').openConnection()
        def json = new JsonBuilder()
        json name: 'newuser', email: 'newuser@jfrog.com', password: "password", groups: ['readers']
        conn.setDoOutput(true)
        conn.setRequestMethod('PUT')
        conn.setRequestProperty('Authorization', auth)
        conn.getOutputStream().write(json.toString().bytes)
        conn.getResponseCode()

        when:
        conn = new URL(baseurl + '/api/plugins/execute/getCurrentUserDetails').openConnection()
        conn.setRequestProperty('Authorization', userauth)
        conn.getResponseCode()
        json = new JsonSlurper().parse(conn.getInputStream())

        then:
        json.username == 'newuser'
        json.email == 'newuser@jfrog.com'
        json.groups.contains('readers')
        json.lastLoginClientIp == null
        json.lastAccessClientIp == null
        json.lastLoginTimeMillis == 0
        json.lastAccessTimeMillis == 0
        json.transientUser == false
        json.anonymous == false
        json.admin == false
        json.enabled == true
        json.updatableProfile == true
        json.privateKey == null
        json.bintrayAuth == null
        json.realm == null

        cleanup:
        conn = new URL(baseurl + '/api/security/users/newuser').openConnection()
        conn.setRequestMethod('DELETE')
        conn.setRequestProperty('Authorization', auth)
        conn.getResponseCode()
    }
}
