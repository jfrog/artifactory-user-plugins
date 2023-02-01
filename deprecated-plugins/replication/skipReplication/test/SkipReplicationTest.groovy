import spock.lang.Specification
import org.jfrog.artifactory.client.model.repository.settings.impl.MavenRepositorySettingsImpl
import org.apache.http.client.HttpResponseException

import org.jfrog.artifactory.client.ArtifactoryClientBuilder


class SkipReplicationTest extends Specification {
    def 'skip replication test'() {
        setup:
        def baseurl1 = 'http://localhost:8088/artifactory'
        def baseurl2 = 'http://localhost:8081/artifactory'
        def replicationurl = 'http://localhost:8081/artifactory'
        def artifactory1 = ArtifactoryClientBuilder.create().setUrl(baseurl1).setUsername('admin').setPassword('password').build()
        def artifactory2 = ArtifactoryClientBuilder.create().setUrl(baseurl2).setUsername('admin').setPassword('password').build()
        def auth = "Basic ${'admin:password'.bytes.encodeBase64()}"
        def xmlfile = new File('./src/test/groovy/SkipReplicationTest/maven-metadata.xml')
        def jarfile = new File('./src/test/groovy/SkipReplicationTest/lib-aopalliance-1.0.jar')

        def builder1 = artifactory1.repositories().builders()
        def local = builder1.localRepositoryBuilder().key('maven-local')
        .repositorySettings(new MavenRepositorySettingsImpl()).build()
        artifactory1.repositories().create(0, local)

        when:
        def conn = new URL("${baseurl1}/api/replications/maven-local").openConnection()
        conn.requestMethod = 'PUT'
        conn.doOutput = true
        conn.setRequestProperty('Authorization', auth)
        conn.setRequestProperty('Content-Type', 'application/json')

        def textFile = "{\"url\" : \"${replicationurl}/maven-copy\","
        textFile += '"socketTimeoutMillis" : 15000,'
        textFile += '"username" : "admin",'
        textFile += '"password" : "password",'
        textFile += '"enableEventReplication" : true,'
        textFile += '"enabled" : true,'
        textFile += '"cronExp" : "0 0 12 * * ?",'
        textFile += '"syncDeletes" : true,'
        textFile += '"syncProperties" : true,'
        textFile += '"syncStatistics" : false,'
        textFile += '"repoKey" : "maven-local"}'
        conn.outputStream.write(textFile.bytes)
        assert conn.responseCode == 201
        conn.disconnect()

        def builder2 = artifactory2.repositories().builders()
        def copy = builder2.localRepositoryBuilder().key('maven-copy')
        .repositorySettings(new MavenRepositorySettingsImpl()).build()
        artifactory2.repositories().create(0, copy)

        artifactory1.repository("maven-local")
        .upload("maven-metadata.xml", xmlfile)
        .withProperty("prop", "test")
        .doUpload()
        artifactory1.repository("maven-local")
        .upload("lib-aopalliance-1.0.jar", jarfile)
        .withProperty("prop", "test")
        .doUpload()

        conn = new URL("${baseurl1}/api/replication/execute/maven-local").openConnection()
        conn.requestMethod = 'POST'
        conn.doOutput = true
        conn.setRequestProperty('Authorization', auth)
        conn.setRequestProperty('Content-Type', 'application/json')
        textFile = "[{\"url\" : \"${replicationurl}/maven-copy\","
        textFile += '"username" : "admin",'
        textFile += '"password" : "password",'
        textFile += '"delete" : true,'
        textFile += '"properties" : true}]'
        conn.outputStream.write(textFile.bytes)
        assert conn.responseCode == 202
        conn.disconnect()
        System.sleep(20000)
        artifactory2.repository("maven-copy").file("maven-metadata.xml").info()

        then:
        thrown(HttpResponseException)
        artifactory2.repository("maven-copy").file("lib-aopalliance-1.0.jar").info()

        cleanup:
        artifactory1.repository("maven-local").delete()
        artifactory2.repository("maven-copy").delete()
    }
}
