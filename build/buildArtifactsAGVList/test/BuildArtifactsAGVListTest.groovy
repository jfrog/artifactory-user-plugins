import spock.lang.Specification
import groovy.json.JsonSlurper
import org.jfrog.artifactory.client.ArtifactoryClientBuilder
import org.jfrog.artifactory.client.model.repository.settings.impl.MavenRepositorySettingsImpl

class BuildArtifactsAGVListTest extends Specification {
    static final baseurl = 'http://localhost:8088/artifactory'
    static final auth   = "Basic ${'admin:password'.bytes.encodeBase64()}"

    def 'test build artifacts AGVList '() {

        setup:
        def artifactory = ArtifactoryClientBuilder.create().setUrl(baseurl)
            .setUsername('admin').setPassword('password').build()
        def builder = artifactory.repositories().builders()
        def local = builder.localRepositoryBuilder().key('libs-snapshots-local')
                .repositorySettings(new MavenRepositorySettingsImpl()).build()
        artifactory.repositories().create(0, local)

        def pom = new File('./src/test/groovy/BuildArtifactsAGVListTest/multi-2.17-SNAPSHOT.pom')
        def path = "org/jfrog/test/multi/2.17-SNAPSHOT/multi-2.17-SNAPSHOT.pom"
        artifactory.repository("libs-snapshots-local").upload(path,pom).doUpload();


        def buildInfoFile = new File("./src/test/groovy/BuildArtifactsAGVListTest/build-info.json")
        def artifactList_buildInfo = new HashMap<String,List>()
        def object = new JsonSlurper().parse(buildInfoFile)
        def buildName = object.name
        def buildNumber = object.number
        object.modules.each {
            def a = it.id.trim().split(':').collect{ it.trim()}
            def groupid = a[0]
            def artifactid = a[1]
            def version = a[2].trim().split('-').collect {it.trim()}
            artifactList_buildInfo.put(artifactid,["groupId":groupid,"version":version[0]])
        }

        def conn = new URL("${baseurl}/api/build").openConnection()
        conn.requestMethod = 'PUT'
        conn.doOutput = true
        conn.setRequestProperty('Authorization', auth)
        conn.setRequestProperty('Content-Type', 'application/json')
        buildInfoFile.withInputStream { conn.outputStream << it }
        assert conn.responseCode == 204
        conn.disconnect()


        when:
        conn = new URL("${baseurl}/api/plugins/execute/MavenDep?params=buildName=${buildName}%7CbuildNumber=${buildNumber}").openConnection()
        conn.requestMethod = 'POST'
        conn.doOutput = true
        conn.setRequestProperty('Authorization', auth)
        conn.setRequestProperty('Content-Type', 'application/json')
        assert conn.responseCode == 200
        def result = conn.getInputStream().text
        conn.disconnect()

        then:
        def resultObject = new JsonSlurper().parseText(result).every { art ->
            def orig = artifactList_buildInfo[art.artifactId]
            orig && orig.groupId == art.groupId && orig.version == art.version.trim().split('-').collect { it.trim()}
        }

        cleanup:
        artifactory.repository("libs-snapshots-local").delete()
        conn = new URL("${baseurl}/api/build/${buildName}?buildNumbers=${buildNumber}").openConnection()
        conn.requestMethod = 'DELETE'
        conn.setRequestProperty('Authorization', auth)
        assert conn.responseCode == 200
        conn.disconnect()

    }
}
