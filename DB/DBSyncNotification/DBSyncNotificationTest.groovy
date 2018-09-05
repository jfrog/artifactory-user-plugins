import groovy.json.JsonSlurper
import spock.lang.Specification

class DBSyncNotificationTest extends Specification {
    def 'db sync notif test'() {
        when:
        def auth = "Basic ${'admin:password'.bytes.encodeBase64()}"
        def logs = 'http://localhost:8088/artifactory/api/systemlogs/logData?id=artifactory.log'
        def sync = 'http://localhost:8081/artifactory/api/plugins/execute/syncNotification'
        def lastline = null, conn = null, lastlinum = -1, startlinum = -1
        // the test won't pass unless we wait for the HA nodes to connect
        def starttime = System.currentTimeMillis()
        while (true) {
            conn = new URL(logs).openConnection()
            conn.setRequestProperty('Authorization', auth)
            assert conn.responseCode == 200
            def oldlines = new JsonSlurper().parse(conn.inputStream).logContent.readLines()
            conn.disconnect()
            lastline = oldlines.findAll { it ==~ /.*\d\d\d\d-\d\d-\d\d \d\d:\d\d:\d\d.*/ }[-1]
            lastlinum = oldlines.findIndexOf { it == lastline }
            startlinum = oldlines.findIndexOf { it.contains('Artifactory successfully started') }
            if (lastlinum > startlinum) break
            if (System.currentTimeMillis() > starttime + 120000) {
                if (startlinum > -1) {
                    break
                } else {
                    throw new Exception("Failed to get last log line after Artifactory startup")
                }
            }
            System.sleep(5000)
        }

        conn = new URL(sync).openConnection()
        conn.requestMethod = 'POST'
        conn.setRequestProperty('Authorization', auth)
        assert conn.responseCode == 200
        conn.disconnect()
        System.sleep(1000)
        conn = new URL(logs).openConnection()
        conn.setRequestProperty('Authorization', auth)
        assert conn.responseCode == 200
        def lines = new JsonSlurper().parse(conn.inputStream).logContent.readLines()
        conn.disconnect()
        def idx = lines.findIndexOf { it == lastline }
        def newlines = lines[idx + 1 .. -1]

        then:
        idx > 0
        newlines.size() > 0
        newlines.any {
            it.contains('Artifactory application context set to NOT READY by reload') || it.contains('Reloading configuration...')
        }
        newlines.any {
            it.contains('Artifactory application context set to READY by reload') || it.contains('Configuration reloaded.')
        }
    }
}
