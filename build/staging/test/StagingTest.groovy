import groovy.json.JsonSlurper
import org.jfrog.artifactory.client.Artifactory
import org.jfrog.artifactory.client.model.repository.settings.impl.MavenRepositorySettingsImpl
import spock.lang.Specification

class StagingTest extends Specification {

    static final baseurl = 'http://localhost:8088/artifactory'
    static final auth = "Basic ${'admin:password'.bytes.encodeBase64()}"

    def setupSpec() {
        addBuild('build-1.0.0.json')
        addBuild('build-1.0.1.json')
        addBuild('build-1.1.0.json')
    }

    def cleanupSpec() {
        deleteAllBuilds('my-project')
    }

    def 'simple maven staging strategy test'() {
        when:
        def buildStagingStrategy = getBuildStagingStrategy('simpleMaven', 'my-project')
        def buildStagingStragegyJson = new JsonSlurper().parseText(buildStagingStrategy)

        then:
        buildStagingStragegyJson.defaultModuleVersion.nextRelease == '1.1.1'
        buildStagingStragegyJson.defaultModuleVersion.nextDevelopment == '1.1.1-SNAPSHOT'
    }

    def 'detailed maven staging strategy test'() {
        when:
        def buildStagingStrategy = getBuildStagingStrategy('detailedMaven', 'my-project')
        def buildStagingStragegyJson = new JsonSlurper().parseText(buildStagingStrategy)
        def moduleInfo = buildStagingStragegyJson.moduleVersionsMap['org.jfrog.test:my-project']

        then:
        moduleInfo.nextRelease == '1.1.1-0'
        moduleInfo.nextDevelopment == '1.1.1-SNAPSHOT'
    }

    def 'gradle no patch staging strategy test'() {
        when:
        def buildStagingStrategy = getBuildStagingStrategy('gradle', 'my-project', false)
        def buildStagingStragegyJson = new JsonSlurper().parseText(buildStagingStrategy)
        def moduleInfo = buildStagingStragegyJson.moduleVersionsMap['modulex']

        then:
        moduleInfo.nextRelease == '1.1.0a'
        moduleInfo.nextDevelopment == '1.1.1-SNAPSHOT'
    }

    private void createLocalMavenRepository(Artifactory artifactory, String name) {
        def builder = artifactory.repositories().builders()
        def local = builder.localRepositoryBuilder().key(name)
                .repositorySettings(new MavenRepositorySettingsImpl()).build()
        artifactory.repositories().create(0, local)
    }

    private void addBuild(String file) {
        def conn = new URL("${baseurl}/api/build").openConnection()
        conn.requestMethod = 'PUT'
        conn.doOutput = true
        conn.setRequestProperty('Authorization', auth)
        conn.setRequestProperty('Content-Type', 'application/json')
        def jsonfile = new File("./src/test/groovy/StagingTest/${file}")
        jsonfile.withInputStream { conn.outputStream << it }
        assert conn.responseCode == 204
        conn.disconnect()
    }

    private void deleteAllBuilds(String buildName) {
        def conn = new URL("${baseurl}/api/build/${buildName}?deleteAll=1").openConnection()
        conn.requestMethod = 'DELETE'
        conn.setRequestProperty('Authorization', auth)
        assert conn.responseCode == 200
        conn.disconnect()
    }

    def getBuildStagingStrategy(strategyName, buildName, patch = false) {
        def url = "${baseurl}/api/plugins/build/staging/$strategyName?buildName=$buildName"

        if (patch != null) {
            url = "$url&params=patch=$patch"
        }

        def conn = new URL(url).openConnection()
        conn.requestMethod = 'GET'
        conn.setRequestProperty('Authorization', auth)
        assert conn.responseCode == 200
        def strategy = conn.getInputStream().text
        conn.disconnect()
        return strategy
    }
}
