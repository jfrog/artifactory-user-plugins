package SecurityReplicationTest
import spock.lang.Specification
import groovy.json.JsonSlurper

import static org.jfrog.artifactory.client.ArtifactoryClient.create

class SecurityReplicationTest extends Specification {
  def 'update extract diff patch test'() {
    setup:
    def baseurl = 'http://localhost:8088/artifactory/api/plugins/execute'
    def auth = "Basic ${'admin:password'.bytes.encodeBase64()}"
    def conn = new URL("http://localhost:8088/artifactory/api/system/version").openConnection()
    conn.requestMethod = 'GET'
    conn.setRequestProperty('Authorization', auth)
    assert conn.responseCode == 200
    def version = new JsonSlurper().parse(conn.inputStream).version.split('\\.')
    conn.disconnect()
    def major = version[0] as int
    def minor = version[1] as int
    def vtest = (major > 5 || (major == 5 && minor >= 6)) ? '' : 'old'
    conn = new URL("$baseurl/testSecurityDump").openConnection()
    conn.requestMethod = 'GET'
    conn.setRequestProperty('Authorization', auth)
    assert conn.responseCode == 200
    def original = conn.inputStream.text, file = null, snapshot = null
    conn.disconnect()

    when:
    conn = new URL("$baseurl/testDBUpdate").openConnection()
    conn.requestMethod = 'POST'
    conn.doOutput = true
    conn.setRequestProperty('Authorization', auth)
    conn.setRequestProperty('Content-Type', 'application/json')
    file = new File("./src/test/groovy/SecurityReplicationTest/sec1${vtest}.json")
    conn.outputStream << file.text
    assert conn.responseCode == 200
    conn.disconnect()

    conn = new URL("$baseurl/testSecurityDump").openConnection()
    conn.requestMethod = 'GET'
    conn.setRequestProperty('Authorization', auth)
    assert conn.responseCode == 200
    snapshot = conn.inputStream.text
    conn.disconnect()

    then:
    file.text == snapshot

    when:
    conn = new URL("$baseurl/testDBUpdate").openConnection()
    conn.requestMethod = 'POST'
    conn.doOutput = true
    conn.setRequestProperty('Authorization', auth)
    conn.setRequestProperty('Content-Type', 'application/json')
    file = new File("./src/test/groovy/SecurityReplicationTest/sec2${vtest}.json")
    conn.outputStream << file.text
    assert conn.responseCode == 200
    conn.disconnect()

    conn = new URL("$baseurl/testSecurityDump").openConnection()
    conn.requestMethod = 'GET'
    conn.setRequestProperty('Authorization', auth)
    assert conn.responseCode == 200
    snapshot = conn.inputStream.text
    conn.disconnect()

    then:
    file.text == snapshot

    cleanup:
    conn = new URL("$baseurl/testDBUpdate").openConnection()
    conn.requestMethod = 'POST'
    conn.doOutput = true
    conn.setRequestProperty('Authorization', auth)
    conn.setRequestProperty('Content-Type', 'application/json')
    conn.outputStream << original
    assert conn.responseCode == 200
    conn.disconnect()
  }
}
