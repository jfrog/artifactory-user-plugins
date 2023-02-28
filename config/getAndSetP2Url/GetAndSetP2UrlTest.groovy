import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import spock.lang.Specification

class GetAndSetP2UrlTest extends Specification {
    def 'P2 URL get/set test'() {
        setup:
        def auth = "Basic ${'admin:password'.bytes.encodeBase64()}"
        def repourl = 'http://localhost:8088/artifactory/api/repositories/'
        def configs = [
            [key: 'p2-local', rclass: 'local', packageType: 'maven'],
            [key: 'p2-remote', rclass: 'remote', packageType: 'p2',
            url: 'http://localhost:8088/artifactory/p2-local',
            username: 'admin', password: 'password'],
            [key: 'p2-virtual', rclass: 'virtual', packageType: 'p2']]
        for (conf in configs) {
            def conn = new URL("$repourl${conf['key']}").openConnection()
            conn.doOutput = true
            conn.requestMethod = 'PUT'
            conn.setRequestProperty('Authorization', auth)
            conn.setRequestProperty('Content-Type', 'application/json')
            conn.outputStream.bytes = new JsonBuilder(conf).toString().bytes
            assert conn.responseCode < 300
            conn.disconnect()
        }

        when:
        def exurl = 'http://localhost:8088/artifactory/api/plugins/execute/'
        def urls = ['local://nonexistent', 'local://p2-local']
        urls << 'http://localhost:8088/artifactory/p2-remote'
        def cnfg = [repo: 'p2-virtual', urls: urls]
        def cnct = new URL("${exurl}modifyP2Urls").openConnection()
        cnct.doOutput = true
        cnct.requestMethod = 'POST'
        cnct.setRequestProperty('Authorization', auth)
        cnct.setRequestProperty('Content-Type', 'application/json')
        cnct.outputStream.bytes = new JsonBuilder(cnfg).toString().bytes
        assert cnct.responseCode < 300
        def modresponse = new JsonSlurper().parse(cnct.inputStream)
        cnct.disconnect()
        def params = "?params=repo=p2-virtual"
        cnct = new URL("${exurl}getP2Urls$params").openConnection()
        cnct.requestMethod = 'POST'
        cnct.setRequestProperty('Authorization', auth)
        assert cnct.responseCode < 300
        def getresponse = new JsonSlurper().parse(cnct.inputStream)
        cnct.disconnect()

        then:
        modresponse.repo == 'p2-virtual'
        modresponse.valid == true
        modresponse.urls.size() == 2
        modresponse.urls.contains('local://p2-local')
        modresponse.urls.contains('http://localhost:8088/artifactory/p2-remote')
        getresponse.repo == modresponse.repo
        getresponse.valid == modresponse.valid
        getresponse.urls.size() == modresponse.urls.size()
        getresponse.urls.containsAll(modresponse.urls)

        cleanup:
        for (conf in configs) {
            def conn = new URL("$repourl${conf['key']}").openConnection()
            conn.requestMethod = 'DELETE'
            conn.setRequestProperty('Authorization', auth)
            conn.responseCode
            conn.disconnect()
        }
    }
}
