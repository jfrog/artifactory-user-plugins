import spock.lang.Specification

import static org.jfrog.artifactory.client.ArtifactoryClient.create

class SecurityReplicationTest extends Specification {
  def 'put and get security replication list test'() {
    setup:
    def baseurl = 'http://localhost:8088/artifactory/api/plugins/execute'
    def auth = "Basic ${'admin:password'.bytes.encodeBase64()}"
    def conn = null

    when:
    conn = new URL("${baseurl}/securityReplication").openConnection()
    conn.requestMethod = 'PUT'
    conn.doOutput = true
    conn.setRequestProperty('Authorization', auth)
    def textFile = []
    textFile << "http://localhost:8080/artifactory"
    textFile << "http://localhost:8081/artifactory"
    textFile << "http://localhost:8082/artifactory"
    conn.outputStream.write(textFile.toString().bytes)
    assert conn.responseCode == 200
    conn.disconnect()

    conn = new URL("${baseurl}/securityReplicationList").openConnection()
    conn.requestMethod = 'GET'
    conn.setRequestProperty('Authorization', auth)
    assert conn.responseCode == 200
    def reader = new InputStreamReader(conn.inputStream)
    def textFile2 = reader.text
    conn.disconnect()

    then:
    textFile.toString() == textFile2
  }
}
