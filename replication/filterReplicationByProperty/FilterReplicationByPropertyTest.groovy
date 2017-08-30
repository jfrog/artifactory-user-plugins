import groovyx.net.http.HttpResponseException
import org.jfrog.artifactory.client.model.repository.settings.impl.MavenRepositorySettingsImpl
import spock.lang.Shared
import spock.lang.Specification

import static org.jfrog.artifactory.client.ArtifactoryClient.create

class FilterReplicationByPropertyTest extends Specification {

   def 'filter replication by property test'() {
        setup:
        def baseurl1 = 'http://localhost:8088/artifactory'
        def baseurl2 = 'http://localhost:8082/artifactory'
        def artifactory1 = create(baseurl1, 'admin', 'password')
        def artifactory2 = create(baseurl2, 'admin', 'password')
        def auth = "Basic ${'admin:password'.bytes.encodeBase64()}"
        def jarfile = new File('/Users/jainishs/Downloads/lib-aopalliance-1.0.jar')

        def builder1 = artifactory1.repositories().builders()
        def local = builder1.localRepositoryBuilder().key('libs-release-local')
        .repositorySettings(new MavenRepositorySettingsImpl()).build()
        artifactory1.repositories().create(0, local)

        when:
        def conn = new URL("${baseurl1}/api/replications/libs-release-local").openConnection()
        conn.requestMethod = 'PUT'
        conn.doOutput = true
        conn.setRequestProperty('Authorization', auth)
        conn.setRequestProperty('Content-Type', 'application/json')

        def textFile = "{\"url\" : \"${baseurl2}/libs-release-local\","
        textFile += '"socketTimeoutMillis" : 15000,'
        textFile += '"username" : "admin",'
        textFile += '"password" : "password",'
        textFile += '"enableEventReplication" : true,'
        textFile += '"enabled" : true,'
        textFile += '"cronExp" : "0 0 12 * * ?",'
        textFile += '"syncDeletes" : true,'
        textFile += '"syncProperties" : true,'
        textFile += '"syncStatistics" : false,'
        textFile += '"repoKey" : "libs-release-local"}'
        conn.outputStream.write(textFile.bytes)
        assert conn.responseCode == 201
        conn.disconnect()

        def builder2 = artifactory2.repositories().builders()
        def copy = builder2.localRepositoryBuilder().key('libs-release-local')
        .repositorySettings(new MavenRepositorySettingsImpl()).build()
        artifactory2.repositories().create(0, copy)

        artifactory1.repository("libs-release-local")
        .upload("lib-aopalliance-1.0.jar", jarfile)
        .withProperty("foo", "true")
        .doUpload()

        then:
        artifactory2.repository("libs-release-local").file("lib-aopalliance-1.0.jar").info()

        cleanup:
        artifactory1.repository("libs-release-local").delete()
        artifactory2.repository("libs-release-local").delete()
    }
}
