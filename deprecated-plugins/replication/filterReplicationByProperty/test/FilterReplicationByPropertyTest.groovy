import org.jfrog.artifactory.client.model.repository.settings.impl.MavenRepositorySettingsImpl
import spock.lang.Specification
import org.apache.http.client.HttpResponseException

import org.jfrog.artifactory.client.ArtifactoryClientBuilder

class FilterReplicationByPropertyTest extends Specification {

   def 'filter replication by property test'() {
        setup:
        def baseurl1 = 'http://localhost:8088/artifactory'
        def baseurl2 = 'http://localhost:8081/artifactory'
        def replicationurl = 'http://localhost:8081/artifactory'
        def artifactory1 = ArtifactoryClientBuilder.create().setUrl(baseurl1).setUsername('admin').setPassword('password').build()
        def artifactory2 = ArtifactoryClientBuilder.create().setUrl(baseurl2).setUsername('admin').setPassword('password').build()
        def auth = "Basic ${'admin:password'.bytes.encodeBase64()}"
        def jarfile = new File('./src/test/groovy/FilterReplicationByPropertyTest/lib-aopalliance-1.0.jar')

        def builder1 = artifactory1.repositories().builders()
        def local = builder1.localRepositoryBuilder().key('libs-releases-local')
        .repositorySettings(new MavenRepositorySettingsImpl()).build()
        artifactory1.repositories().create(0, local)

        def builder2 = artifactory2.repositories().builders()
        def copy = builder2.localRepositoryBuilder().key('libs-releases-local')
                .repositorySettings(new MavenRepositorySettingsImpl()).build()
        artifactory2.repositories().create(0, copy)

        def conn = new URL("${baseurl1}/api/replications/libs-releases-local").openConnection()
        conn.requestMethod = 'PUT'
        conn.doOutput = true
        conn.setRequestProperty('Authorization', auth)
        conn.setRequestProperty('Content-Type', 'application/json')

        def textFile = "{\"url\" : \"${replicationurl}/libs-releases-local\","
        textFile += '"socketTimeoutMillis" : 15000,'
        textFile += '"username" : "admin",'
        textFile += '"password" : "password",'
        textFile += '"enableEventReplication" : true,'
        textFile += '"enabled" : true,'
        textFile += '"cronExp" : "0 0/5 * * * ?",'
        textFile += '"syncDeletes" : true,'
        textFile += '"syncProperties" : true,'
        textFile += '"syncStatistics" : false,'
        textFile += '"repoKey" : "libs-releases-local"}'
        conn.outputStream.write(textFile.bytes)
        assert conn.responseCode == 201
        conn.disconnect()

        when:
        artifactory1.repository("libs-releases-local")
        .upload("lib-aopalliance-1.0.jar", jarfile)
        .doUpload()
        artifactory1.repository("libs-releases-local")
        .upload("lib-aopalliance-2.0.jar", jarfile)
        .withProperty("foo", "true")
        .doUpload()
        conn = new URL("${baseurl1}/api/replication/execute/libs-releases-local").openConnection()
        conn.requestMethod = 'POST'
        conn.doOutput = true
        conn.setRequestProperty('Authorization', auth)
        conn.setRequestProperty('Content-Type', 'application/json')
        textFile = "[{\"url\" : \"${replicationurl}/libs-releases-local\","
        textFile += '"username" : "admin",'
        textFile += '"password" : "password",'
        textFile += '"delete" : true,'
        textFile += '"properties" : true}]'
        conn.outputStream.write(textFile.bytes)
        assert conn.responseCode == 202
        conn.disconnect()
        System.sleep(20000)
        artifactory2.repository("libs-releases-local").file("lib-aopalliance-1.0.jar").info()

        then:
        thrown(HttpResponseException)
        artifactory2.repository("libs-releases-local").file("lib-aopalliance-2.0.jar").info()

        cleanup:
        artifactory1.repository("libs-releases-local").delete()
        artifactory2.repository("libs-releases-local").delete()
    }
}
