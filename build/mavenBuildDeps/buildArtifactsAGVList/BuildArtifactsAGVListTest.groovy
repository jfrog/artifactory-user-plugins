import spock.lang.Specification
import groovy.json.JsonSlurper
import org.jfrog.artifactory.client.ArtifactoryClient.*

class BuildArtifactsAGVListTest extends Specification {
    static final baseurl = 'http://localhost:8088/artifactory'
    static final auth   = "Basic ${'admin:password'.bytes.encodeBase64()}"
    static final build_info_file   = 'build-info.json'

    def 'test build artifacts AGVList '() {
        setup:

        def conn = new URL("${baseurl}/api/build").openConnection()
        conn.requestMethod = 'PUT'
        conn.doOutput = true
        conn.setRequestProperty('Authorization', auth)
        conn.setRequestProperty('Content-Type', 'application/json')
        def buildInfoFile = new File("./src/test/groovy/${build_info_file}")
        buildInfoFile.withInputStream { conn.outputStream << it }
        assert conn.responseCode == 204
        conn.disconnect()

        def artifactList_buildInfo = new ArrayList()
        def object = new JsonSlurper().parse(buildInfoFile)
        def buildName = object.name
        def buildNumber = object.number

        object.modules.each {
            def a = it.id.trim().split(':').collect{ it.trim()}
            def groupid = a[0]
            def artifactid = a[1]
            def version = a[2]
            def AGV = ["groupId":groupid,"artifactId":artifactid,"version":version]
            artifactList_buildInfo.addAll(AGV)
        }

        when:

        conn = new URL("${baseurl}/api/plugins/execute/MavenDep?params=buildName=${buildName};buildNumber=1").openConnection()
        conn.requestMethod = 'POST'
        conn.doOutput = true
        conn.setRequestProperty('Authorization', auth)
        conn.setRequestProperty('Content-Type', 'application/json')
        assert conn.responseCode == 200
        def result = conn.getInputStream().text
        conn.disconnect()

        then:

        def resultObject = new JsonSlurper().parseText(result)
        for (artifacts in resultObject){
           for (orig_artifacts in artifactList_buildInfo){
                if (orig_artifacts.artifactId==artifacts.artifactId){
                   assert orig_artifacts.groupId == artifacts.groupId
                    def front_version = artifacts.version.trim().split('-').collect{it.trim()}
                    def orig_front_version = orig_artifacts.version.trim().split('-').collect{it.trim()}
                   assert orig_front_version[0] == front_version[0]
                }
            }
        }

        cleanup:

        conn = new URL("${baseurl}/api/build/${buildName}?buildNumbers=${buildNumber}").openConnection()
        conn.requestMethod = 'DELETE'
        conn.setRequestProperty('Authorization', auth)
        assert conn.responseCode == 200
        conn.disconnect()

    }
}
