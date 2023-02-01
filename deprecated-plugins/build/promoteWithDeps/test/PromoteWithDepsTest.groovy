import groovy.json.JsonBuilder
import org.apache.http.client.HttpResponseException
import org.jfrog.artifactory.client.ItemHandle
import org.jfrog.artifactory.client.model.repository.settings.impl.MavenRepositorySettingsImpl
import spock.lang.Shared
import spock.lang.Specification

import org.jfrog.artifactory.client.ArtifactoryClientBuilder

class PromoteWithDepsTest extends Specification {

    def static final testResourcesBasePath = "./src/test/groovy/PromoteWithDepsTest"
    def static final baseurl = 'http://localhost:8088/artifactory'
    def static final auth = "Basic ${'admin:password'.bytes.encodeBase64()}"
    @Shared def artifactory = ArtifactoryClientBuilder.create().setUrl(baseurl)
          .setUsername('admin').setPassword('password').build()

    def static final stageRepoKey = 'my-snapshot-local'
    def static final releaseRepoKey = 'my-release-local'
    def static final dependencyBuildName = 'maven-dependency-example'
    def static final dependencyBuildNumber = '1'
    def static final dependentBuildName = 'maven-dependent-example'
    def static final dependentBuildNumber = '1'

    def 'promote with build dependencies test'() {
        setup:
        // Create stage repo
        def stageRepo = createLocalRepo(stageRepoKey)
        // Create release repo
        def releaseRepo = createLocalRepo(releaseRepoKey)
        // Set build properties
        def dependencyBuildProperties = [
                "build.number": dependencyBuildNumber,
                "build.name": dependencyBuildName,
                "build.timestamp": "1505520473765"
        ]
        def dependentBuildProperties = [
                "build.number": dependentBuildNumber,
                "build.name": dependentBuildName,
                "build.timestamp": "1505520473765"
        ]

        when:
        // Upload build artifacts
        deploy(stageRepo, 'org/jfrog/test/multi/3.7-SNAPSHOT', 'multi-3.7-SNAPSHOT.pom', dependencyBuildProperties)
        deploy(stageRepo, 'org/jfrog/test/multi1/3.7-SNAPSHOT', 'multi1-3.7-SNAPSHOT.jar', dependentBuildProperties)
        deploy(stageRepo, 'org/jfrog/test/multi1/3.7-SNAPSHOT', 'multi1-3.7-SNAPSHOT.pom', dependentBuildProperties)
        deploy(stageRepo, 'org/jfrog/test/multi2/3.7-SNAPSHOT', 'multi2-3.7-SNAPSHOT.jar', dependentBuildProperties)
        deploy(stageRepo, 'org/jfrog/test/multi2/3.7-SNAPSHOT', 'multi2-3.7-SNAPSHOT.pom', dependentBuildProperties)
        deploy(stageRepo, 'org/jfrog/test/multi3/3.7-SNAPSHOT', 'multi3-3.7-SNAPSHOT.war', dependentBuildProperties)
        deploy(stageRepo, 'org/jfrog/test/multi3/3.7-SNAPSHOT', 'multi3-3.7-SNAPSHOT.pom', dependentBuildProperties)
        // Upload dependency build info
        uploadBuildInfo('dependency-build.json')
        // Upload dependent build info
        uploadBuildInfo('dependent-build.json')
        // Execute dependent build promotion
        promoteBuildWithDependencies(dependentBuildName, dependentBuildNumber, releaseRepoKey)

        then:
        // Check build artifacts have been promoted
        releaseRepo.file('org/jfrog/test/multi1/3.7-SNAPSHOT/multi1-3.7-SNAPSHOT.jar').info() != null
        releaseRepo.file('org/jfrog/test/multi1/3.7-SNAPSHOT/multi1-3.7-SNAPSHOT.pom').info() != null
        releaseRepo.file('org/jfrog/test/multi2/3.7-SNAPSHOT/multi2-3.7-SNAPSHOT.jar').info() != null
        releaseRepo.file('org/jfrog/test/multi2/3.7-SNAPSHOT/multi2-3.7-SNAPSHOT.pom').info() != null
        releaseRepo.file('org/jfrog/test/multi3/3.7-SNAPSHOT/multi3-3.7-SNAPSHOT.war').info() != null
        releaseRepo.file('org/jfrog/test/multi3/3.7-SNAPSHOT/multi3-3.7-SNAPSHOT.pom').info() != null
        // Check build dependencies artifacts have also been promoted
        releaseRepo.file('org/jfrog/test/multi/3.7-SNAPSHOT/multi-3.7-SNAPSHOT.pom').info() != null

        cleanup:
        // Remove build info
        ignoringExceptions { deleteBuildInfo(dependentBuildName, dependentBuildNumber) }
        ignoringExceptions { deleteBuildInfo(dependencyBuildName, dependencyBuildNumber) }
        // Delete repositories
        ignoringExceptions { artifactory.repository(stageRepoKey)?.delete() }
        ignoringExceptions { artifactory.repository(releaseRepoKey)?.delete() }

    }

    /**
     * Create local repo
     * @param repoKey
     * @return
     */
    def createLocalRepo(String repoKey) {
        if (!isRepoCreated(repoKey)) {
            def builder = artifactory.repositories().builders()
            def local = builder.localRepositoryBuilder().key(repoKey)
                    .repositorySettings(new MavenRepositorySettingsImpl()).build()
            artifactory.repositories().create(0, local)
        }
        return artifactory.repository(repoKey)
    }

    /**
     * Check if repo exists
     * @param repoKey
     * @return
     */
    def isRepoCreated(String repoKey) {
        try {
            return artifactory.repository(repoKey).get() != null
        } catch (HttpResponseException e) {
            return false
        }
    }

    /**
     * Deploy artifact
     * @param repo
     * @param path
     * @param fileName
     * @return
     */
    def deploy(def repo, def path, def fileName, Map properties) {
        File file = new File("$testResourcesBasePath/$fileName")
        repo.upload("$path/$fileName", file.newInputStream()).doUpload()

        ItemHandle artifact = repo.file("$path/$fileName")
        for (property in properties) {
            artifact.properties().addProperty(property.key, property.value).doSet()
        }
    }

    /**
     * Upload build info
     * @param fileName
     * @return
     */
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

    /**
     * Delete build info
     * @param buildName
     * @param buildNumber
     * @return
     */
    def deleteBuildInfo(def buildName, def buildNumber) {
        def conn = new URL("${baseurl}/api/build/${buildName}?buildNumbers=${buildNumber}").openConnection()
        conn.requestMethod = 'DELETE'
        conn.setRequestProperty('Authorization', auth)
        conn.responseCode
        conn.disconnect()
    }

    /**
     * Promote build with Dependencies
     * @param buildName
     * @param buildNumber
     * @param targetRepo
     * @return
     */
    def promoteBuildWithDependencies(def buildName, def buildNumber, def targetRepo) {
        def url = "$baseurl/api/plugins/execute/promoteWithDeps?params=buildName=$buildName%7CbuildNumber=$buildNumber"
        def json = new JsonBuilder([
            targetRepo: targetRepo,
            status: "promoted",
            comment: "Promotion test",
            ciUser: "jenkins",
            timestamp: "2017-09-16T00:07:53.765+0000",
            copy: true,
            artifacts: true,
            dependencies: false
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
