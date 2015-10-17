import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import spock.lang.Specification

import static org.jfrog.artifactory.client.ArtifactoryClient.create

class BackupsConfigTest extends Specification {
    def 'backups plugin test'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory/api/plugins/execute'
        def params = '?params=key=newbackup'
        def auth = "Basic ${'admin:password'.bytes.encodeBase64()}"
        def conn = null

        when:
        // get an initial list of backups
        conn = new URL("$baseurl/getBackupsList").openConnection()
        conn.setRequestProperty('Authorization', auth)
        assert conn.responseCode == 200
        def listreader1 = new InputStreamReader(conn.inputStream)
        def jsonlist1 = new JsonSlurper().parse(listreader1)
        jsonlist1.sort()
        conn.disconnect()
        // add a new backup to the list
        def json1 = [
            key: 'newbackup',
            enabled: true,
            dir: '/path/to/backup/dir',
            cronExp: '0 0 0 * * ?',
            retentionPeriodHours: 168,
            createArchive: false,
            excludedRepositories: null,
            sendMailOnError: true,
            excludeBuilds: true,
            excludeNewRepositories: false]
        conn = new URL("$baseurl/addBackup").openConnection()
        conn.doOutput = true
        conn.setRequestProperty('Authorization', auth)
        conn.setRequestProperty('Content-Type', 'application/json')
        conn.outputStream.write(new JsonBuilder(json1).toString().bytes)
        assert conn.responseCode == 200
        conn.disconnect()
        // get a new backup list, containing the new backup
        conn = new URL("$baseurl/getBackupsList").openConnection()
        conn.setRequestProperty('Authorization', auth)
        assert conn.responseCode == 200
        def listreader2 = new InputStreamReader(conn.inputStream)
        def jsonlist2 = new JsonSlurper().parse(listreader2)
        jsonlist2.sort()
        conn.disconnect()
        // get the new backup data
        conn = new URL("$baseurl/getBackup$params").openConnection()
        conn.setRequestProperty('Authorization', auth)
        assert conn.responseCode == 200
        def reader1r = new InputStreamReader(conn.inputStream)
        def json1r = new JsonSlurper().parse(reader1r)
        conn.disconnect()
        // update the new backup
        def json2diff = [cronExp: '* * * * * ?', createArchive: true,
                         excludedRepositories: ['libs-release-local']]
        def json2 = json1 + json2diff
        conn = new URL("$baseurl/updateBackup$params").openConnection()
        conn.doOutput = true
        conn.requestMethod = 'POST'
        conn.setRequestProperty('Authorization', auth)
        conn.setRequestProperty('Content-Type', 'application/json')
        conn.outputStream.write(new JsonBuilder(json2diff).toString().bytes)
        assert conn.responseCode == 200
        conn.disconnect()
        // get the backup list again, still containing the new backup
        conn = new URL("$baseurl/getBackupsList").openConnection()
        conn.setRequestProperty('Authorization', auth)
        assert conn.responseCode == 200
        def listreader2r = new InputStreamReader(conn.inputStream)
        def jsonlist2r = new JsonSlurper().parse(listreader2r)
        jsonlist2r.sort()
        conn.disconnect()
        // get the modified backup data
        conn = new URL("$baseurl/getBackup$params").openConnection()
        conn.setRequestProperty('Authorization', auth)
        assert conn.responseCode == 200
        def reader2r = new InputStreamReader(conn.inputStream)
        def json2r = new JsonSlurper().parse(reader2r)
        conn.disconnect()
        // delete the new backup
        conn = new URL("$baseurl/deleteBackup$params").openConnection()
        conn.requestMethod = 'DELETE'
        conn.setRequestProperty('Authorization', auth)
        assert conn.responseCode == 200
        conn.disconnect()
        // get the backup list again, not containing the new backup
        conn = new URL("$baseurl/getBackupsList").openConnection()
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
        !('newbackup' in jsonlist1)
        'newbackup' in jsonlist2
    }
}
