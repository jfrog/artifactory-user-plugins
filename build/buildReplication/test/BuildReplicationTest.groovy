import spock.lang.Specification
import groovyx.net.http.HttpResponseException

import static org.jfrog.artifactory.client.ArtifactoryClient.create

class BuildReplicationTest extends Specification {
    def 'build replication test'() {
        setup:
        def baseurl1 = 'http://localhost:8088/artifactory'
        def baseurl2 = 'http://localhost:8081/artifactory'
        def artifactory1 = create(baseurl1, 'admin', 'password')
        def artifactory2 = create(baseurl2, 'admin', 'password')
        def auth = "Basic ${'admin:password'.bytes.encodeBase64()}"

        when:
        def conn1 = new URL("${baseurl1}/api/build").openConnection()
        conn1.requestMethod = 'PUT'
        conn1.doOutput = true
        conn1.setRequestProperty('Authorization', auth)
        conn1.setRequestProperty('Content-Type', 'application/json')
        def jsonfile1 = new File('./src/test/groovy/buildReplicationTest/buildReplication1.json')
        jsonfile1.withInputStream { conn1.outputStream << it }
        assert conn1.responseCode == 204
        conn1.disconnect()

        def proc1 = "curl -X POST -v -u admin:password \"http://localhost:8088/artifactory/api/plugins/execute/buildReplication\""
        def process1 = new ProcessBuilder([ "sh", "-c", proc1])
            .directory(new File("/tmp"))
            .redirectErrorStream(true)
            .start()
       process1.outputStream.close()
       process1.inputStream.eachLine {println it}
       process1.waitFor();

        def conn3 = new URL("${baseurl2}/api/build/buildReplication1/28").openConnection()
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
        def jsonfile2 = new File('./src/test/groovy/buildReplicationTest/buildReplication2.json')
        jsonfile2.withInputStream {conn2.outputStream << it}
        assert conn2.responseCode == 204
        conn2.disconnect()

        //curl -X POST -v -u admin:password "http://localhost:8088/artifactory/api/plugins/execute/buildReplication"

        def proc2 = "curl -X POST -v -u admin:password \"http://localhost:8088/artifactory/api/plugins/execute/buildReplication\""
        def process2 = new ProcessBuilder([ "sh", "-c", proc2])
            .directory(new File("/tmp"))
            .redirectErrorStream(true)
            .start()
        process2.outputStream.close()
        process2.inputStream.eachLine {println it}
        process2.waitFor();

        def conn4 = new URL("${baseurl1}/api/build/buildReplication2/29").openConnection()
        conn4.requestMethod = 'GET'
        conn4.setRequestProperty('Authorization', auth)
        
        then:
        conn4.responseCode == 404
        conn4.disconnect()

        cleanup:
        def conn5 = new URL("${baseurl1}/api/build/buildReplication1?deleteAll=1").openConnection()
        conn5.requestMethod = 'DELETE'
        conn5.setRequestProperty('Authorization', auth)
        assert conn5.responseCode == 200
        conn5.disconnect()

        def conn6 = new URL("${baseurl2}/api/build/buildReplication1?deleteAll=1").openConnection()
        conn6.requestMethod = 'DELETE'
        conn6.setRequestProperty('Authorization', auth)
        assert conn6.responseCode == 200
        conn6.disconnect()

        def conn7 = new URL("${baseurl2}/api/build/buildReplication2?deleteAll=1").openConnection()
        conn7.requestMethod = 'DELETE'
        conn7.setRequestProperty('Authorization', auth)
        assert conn7.responseCode == 200
        conn7.disconnect()
    }
}
