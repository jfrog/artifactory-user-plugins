import groovy.json.JsonBuilder
import org.apache.http.client.HttpResponseException
import org.jfrog.artifactory.client.ItemHandle
import org.jfrog.artifactory.client.model.repository.settings.impl.MavenRepositorySettingsImpl
import spock.lang.Specification
import org.jfrog.artifactory.client.ArtifactoryClientBuilder

class SonarWebhookTest extends Specification {

    def testResourcesBasePath = "./src/test/groovy/SonarWebhookTest"
    def baseurl = 'http://localhost:8088/artifactory'
    def auth = "Basic ${'admin:password'.bytes.encodeBase64()}"
    def artifactory = null

    def 'sonar webhook test'() {
        setup:
        this.artifactory = ArtifactoryClientBuilder.create().setUrl(baseurl).setUsername('admin').setPassword('password').build()
        def stageRepoKey = 'my-snapshot-local'
        def releaseRepoKey = 'my-release-local'
        // Create stage repo
        def stageRepo = createLocalRepo(stageRepoKey)
        // Create release repo
        def releaseRepo = createLocalRepo(releaseRepoKey)
        // Set build properties
        def buildName = 'maven-dependent-example'
        def buildNumber = '1'
        def buildProps = [
                "build.name": buildName,
                "build.number": buildNumber,
                "build.timestamp": "1505520473765"
        ]

        when:
        deploy(stageRepo, 'org/jfrog/test/build/3.7-SNAPSHOT', 'build-3.7-SNAPSHOT.jar', buildProps)
        uploadBuildInfo('build.json')
        triggerPlugin(stageRepoKey, releaseRepoKey)
        sleep(10000)

        then:
        // Check build artifacts have been promoted
        releaseRepo.file('org/jfrog/test/build/3.7-SNAPSHOT/build-3.7-SNAPSHOT.jar').info() != null

        cleanup:
        // Remove build info
        ignoringExceptions { deleteBuildInfo(buildName, buildNumber) }
        // Delete repositories
        ignoringExceptions { artifactory.repository(stageRepoKey)?.delete() }
        ignoringExceptions { artifactory.repository(releaseRepoKey)?.delete() }
    }

    def createLocalRepo(String repoKey) {
        if (!isRepoCreated(repoKey)) {
            def builder = artifactory.repositories().builders()
            def local = builder.localRepositoryBuilder().key(repoKey)
                    .repositorySettings(new MavenRepositorySettingsImpl()).build()
            artifactory.repositories().create(0, local)
        }
        return artifactory.repository(repoKey)
    }

    def isRepoCreated(String repoKey) {
        try {
            return artifactory.repository(repoKey).get() != null
        } catch (HttpResponseException e) {
            return false
        }
    }

    def deploy(def repo, def path, def fileName, Map properties) {
        File file = new File("$testResourcesBasePath/$fileName")
        repo.upload("$path/$fileName", file.newInputStream()).doUpload()

        ItemHandle artifact = repo.file("$path/$fileName")
        for (property in properties) {
            artifact.properties().addProperty(property.key, property.value).doSet()
        }
    }

    def uploadBuildInfo(def fileName) {
        def buildInfoFile = new File("$testResourcesBasePath/$fileName")
        def conn = new URL("${baseurl}/api/build").openConnection()
        conn.requestMethod = 'PUT'
        conn.doOutput = true
        conn.setRequestProperty('Authorization', auth)
        conn.setRequestProperty('Content-Type', 'application/json')
        buildInfoFile.withInputStream { conn.outputStream << it }
        assert conn.responseCode == 204
        conn.disconnect()
    }

    def deleteBuildInfo(def buildName, def buildNumber) {
        def conn = new URL("${baseurl}/api/build/${buildName}?buildNumbers=${buildNumber}").openConnection()
        conn.requestMethod = 'DELETE'
        conn.setRequestProperty('Authorization', auth)
        conn.responseCode
        conn.disconnect()
    }

    def triggerPlugin(sourceRepo, targetRepo) {
        def url = "$baseurl/api/plugins/execute/updateSonarTaskStatus?params=sourceRepo=$sourceRepo%7CtargetRepo=$targetRepo"
        def json = new JsonBuilder([
            taskId: "myTaskId",
            qualityGate: [
                name: "myGate",
                status: "OK",
                conditions: [
                    [metric: "metric1", status: "status1"],
                    [metric: "metric2", status: "status2"]
                ]
            ]
        ])

        def conn = new URL(url).openConnection()
        conn.requestMethod = 'POST'
        conn.setDoOutput(true)
        conn.setRequestProperty('Authorization', auth)
        conn.setRequestProperty('Content-Type', 'application/json')
        conn.outputStream.withWriter { it << json.toString() }
        assert conn.responseCode == 200
        conn.disconnect()
    }

    def ignoringExceptions = { method ->
        try {
            method()
        } catch (Exception e) {
            e.printStackTrace()
        }
    }
}
