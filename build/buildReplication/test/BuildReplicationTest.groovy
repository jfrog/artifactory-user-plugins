import spock.lang.Specification
import groovyx.net.http.HttpResponseException

import org.jfrog.artifactory.client.ArtifactoryClientBuilder

class BuildReplicationTest extends Specification {
    def 'build replication test'() {
        setup:
        def baseurl1 = 'http://localhost:8088/artifactory'
        def baseurl2 = 'http://localhost:8081/artifactory'
        def artifactory1 = ArtifactoryClientBuilder.create().setUrl(baseurl1).setUsername('admin').setPassword('password').build()
        def artifactory2 = ArtifactoryClientBuilder.create().setUrl(baseurl2).setUsername('admin').setPassword('password').build()
        def auth = "Basic ${'admin:password'.bytes.encodeBase64()}"

        when:
        def conn1 = new URL("${baseurl1}/api/build").openConnection()
        conn1.requestMethod = 'PUT'
        conn1.doOutput = true
        conn1.setRequestProperty('Authorization', auth)
        conn1.setRequestProperty('Content-Type', 'application/json')
        def jsonfile1 = new File('./src/test/groovy/BuildReplicationTest/buildReplication1.json')
        jsonfile1.withInputStream { conn1.outputStream << it }
        assert conn1.responseCode == 204
        conn1.disconnect()

        def proc1 = new URL("${baseurl1}/api/plugins/execute/buildReplication").openConnection()
        proc1.requestMethod = 'POST'
        proc1.setRequestProperty('Authorization', auth)
        assert proc1.responseCode == 200
        proc1.disconnect()

        def conn3 = new URL("${baseurl2}/api/build/build%2FReplication1/28").openConnection()
        conn3.requestMethod = 'GET'
        conn3.setRequestProperty('Authorization', auth)
        assert conn3.responseCode == 200
        conn3.disconnect()

        then:
        notThrown(HttpResponseException)

        when:
        def conn2 = new URL("${baseurl2}/api/build").openConnection()
        conn2.requestMethod = 'PUT'
        conn2.doOutput = true
        conn2.setRequestProperty('Authorization', auth)
        conn2.setRequestProperty('Content-Type', 'application/json')
        def jsonfile2 = new File('./src/test/groovy/BuildReplicationTest/buildReplication2.json')
        jsonfile2.withInputStream {conn2.outputStream << it}
        assert conn2.responseCode == 204
        conn2.disconnect()

        def proc2 = new URL("${baseurl1}/api/plugins/execute/buildReplication").openConnection()
        proc2.requestMethod = 'POST'
        proc2.setRequestProperty('Authorization', auth)
        assert proc2.responseCode == 200
        proc2.disconnect()

        def conn4 = new URL("${baseurl1}/api/build/build%2FReplication2/29").openConnection()
        conn4.requestMethod = 'GET'
        conn4.setRequestProperty('Authorization', auth)

        then:
        conn4.responseCode == 404
        conn4.disconnect()

        cleanup:
        def conn5 = new URL("${baseurl1}/api/build/build%2FReplication1?deleteAll=1").openConnection()
        conn5.requestMethod = 'DELETE'
        conn5.setRequestProperty('Authorization', auth)
        assert conn5.responseCode == 200
        conn5.disconnect()

        def conn6 = new URL("${baseurl2}/api/build/build%2FReplication1?deleteAll=1").openConnection()
        conn6.requestMethod = 'DELETE'
        conn6.setRequestProperty('Authorization', auth)
        assert conn6.responseCode == 200
        conn6.disconnect()

        def conn7 = new URL("${baseurl2}/api/build/build%2FReplication2?deleteAll=1").openConnection()
        conn7.requestMethod = 'DELETE'
        conn7.setRequestProperty('Authorization', auth)
        assert conn7.responseCode == 200
        conn7.disconnect()
    }
}
