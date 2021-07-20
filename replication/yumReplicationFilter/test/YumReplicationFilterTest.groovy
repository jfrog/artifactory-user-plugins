import org.jfrog.artifactory.client.model.repository.settings.impl.YumRepositorySettingsImpl
import spock.lang.Specification
import org.jfrog.artifactory.client.ArtifactoryClientBuilder

class YumReplicationFilterTest extends Specification {

   def 'yum replication filter'() {
        setup:
        def baseurl1 = 'http://localhost:8088/artifactory'
        def baseurl2 = 'http://localhost:8081/artifactory'
        def replicationurl = 'http://localhost:8081/artifactory'
        def artifactory1 = ArtifactoryClientBuilder.create().setUrl(baseurl1).setUsername('admin').setPassword('password').build()
        def artifactory2 = ArtifactoryClientBuilder.create().setUrl(baseurl2).setUsername('admin').setPassword('password').build()
        def auth = "Basic ${'admin:password'.bytes.encodeBase64()}"
        def rpmfile = new File('./src/test/groovy/YumReplicationFilterTest/wget-1.19.1-3.fc27.aarch64.rpm')

        def builder1 = artifactory1.repositories().builders()
        def local = builder1.localRepositoryBuilder().key('yum-local')
        .repositorySettings(new YumRepositorySettingsImpl()).build()
        artifactory1.repositories().create(0, local)

        def builder2 = artifactory2.repositories().builders()
        def copy = builder2.localRepositoryBuilder().key('yum-local')
        .repositorySettings(new YumRepositorySettingsImpl()).build()
        artifactory2.repositories().create(0, copy)
        
        def conn = new URL("${baseurl1}/api/replications/yum-local").openConnection()
        conn.requestMethod = 'PUT'
        conn.doOutput = true
        conn.setRequestProperty('Authorization', auth)
        conn.setRequestProperty('Content-Type', 'application/json')

        def textFile = "{\"url\" : \"${replicationurl}/yum-local\","
        textFile += '"socketTimeoutMillis" : 15000,'
        textFile += '"username" : "admin",'
        textFile += '"password" : "password",'
        textFile += '"enableEventReplication" : true,'
        textFile += '"enabled" : true,'
        textFile += '"cronExp" : "0 0/2 * * * ?",'
        textFile += '"syncDeletes" : true,'
        textFile += '"syncProperties" : true,'
        textFile += '"syncStatistics" : false,'
        textFile += '"repoKey" : "yum-local"}'
        conn.outputStream.write(textFile.bytes)
        assert conn.responseCode == 201
        conn.disconnect()

        when:
        artifactory1.repository("yum-local")
        .upload("wget-1.19.1-3.fc27.aarch64.rpm", rpmfile)
        .doUpload()

        conn = new URL("${baseurl1}/api/replication/execute/yum-local").openConnection()
        conn.requestMethod = 'POST'
        conn.doOutput = true
        conn.setRequestProperty('Authorization', auth)
        conn.setRequestProperty('Content-Type', 'application/json')
        textFile = "[{\"url\" : \"${replicationurl}/yum-local\","
        textFile += '"username" : "admin",'
        textFile += '"password" : "password",'
        textFile += '"delete" : true,'
        textFile += '"properties" : true}]'
        conn.outputStream.write(textFile.bytes)
        assert conn.responseCode == 202
        conn.disconnect()
        System.sleep(20000)

        then:
        artifactory2.repository("yum-local").file("wget-1.19.1-3.fc27.aarch64.rpm").info()

        cleanup:
        artifactory1.repository("yum-local").delete()
        artifactory2.repository("yum-local").delete()
    }
}
