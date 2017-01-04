import spock.lang.Specification
import org.jfrog.artifactory.client.model.repository.settings.impl.MavenRepositorySettingsImpl
import groovyx.net.http.HttpResponseException

import static org.jfrog.artifactory.client.ArtifactoryClient.create

class SkipReplicationTest extends Specification {
    def 'skip replication test'() {
        setup:
        def baseurl1 = 'http://localhost:8088/artifactory'
        def baseurl2 = 'http://localhost:8081/artifactory'
        def artifactory1 = create(baseurl1, 'admin', 'password')
        def artifactory2 = create(baseurl2, 'admin', 'password')
        def auth = "Basic ${'admin:password'.bytes.encodeBase64()}"
        def xmlfile = new File('./src/test/groovy/skipReplicationTest/maven-metadata.xml')
        def jarfile = new File('./src/test/groovy/skipReplicationTest/lib-aopalliance-1.0.jar')

        when:
        def conn = new URL("${baseurl2}/api/replications/libs-release-local").openConnection()
        conn.requestMethod = 'PUT'
        conn.doOutput = true
        conn.setRequestProperty('Authorization', auth)
        conn.setRequestProperty('Content-Type', 'application/json')
        def textFile = '{"url" : "${baseurl2}/libs-release-copy",'
        textFile += '"socketTimeoutMillis" : 15000,'
        textFile += '"username" : "admin",'
        textFile += '"password" : "password",'
        textFile += '"enableEventReplication" : false,'
        textFile += '"enabled" : true,'
        textFile += '"cronExp" : "0 0 12 * * ?",'
        textFile += '"syncDeletes" : true,'
        textFile += '"syncProperties" : true,'
        textFile += '"syncStatistics" : false,'
        textFile += '"repoKey" : "libs-release-local"}'
        conn.outputStream.write(textFile.bytes)
        assert conn.responseCode == 201
        conn.disconnect()
        def builder = artifactory2.repositories().builders()
        def copy = builder.localRepositoryBuilder().key('libs-release-copy')
        .repositorySettings(new MavenRepositorySettingsImpl()).build()
        artifactory2.repositories().create(0, copy),,
        artifactory1.repository("libs-release-local")
        .upload("maven-metadata.xml", xmlfile)
        .withProperty("prop", "test")
        .doUpload()
        artifactory1.repository("libs-release-local")
        .upload("lib-aopalliance-1.0.jar", jarfile)
        .withProperty("prop", "test")
        .doUpload()
        artifactory2.repository("libs-release-copy").file("maven-metadata.xml").info()

        then:
        thrown(HttpResponseException)
        artifactory2.repository("libs-release-copy").file("lib-aopalliance-1.0.jar").info()

        cleanup:
        artifactory1.repository("libs-release-local").delete("lib-aopalliance-1.0.jar")
        artifactory1.repository("libs-release-local").delete("maven-metadata.xml")
        artifactory2.repository("libs-release-copy").delete()
        def dlconn = new URL("${baseurl1}/api/replications/libs-release-local").openConnection()
        dlconn.requestMethod = 'DELETE'
        dlconn.setRequestProperty('Authorization', auth)
        assert dlconn.responseCode == 200
        dlconn.disconnect()
    }
}