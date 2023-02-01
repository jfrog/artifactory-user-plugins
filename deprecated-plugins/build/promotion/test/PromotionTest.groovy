import groovy.json.JsonSlurper
import org.apache.http.client.HttpResponseException
import org.jfrog.artifactory.client.ItemHandle
import org.jfrog.artifactory.client.model.repository.settings.impl.MavenRepositorySettingsImpl
import spock.lang.Shared
import spock.lang.Specification

import org.jfrog.artifactory.client.ArtifactoryClientBuilder

class PromotionTest extends Specification {

    def static final baseurl = 'http://localhost:8088/artifactory'
    def static final auth = "Basic ${'admin:password'.bytes.encodeBase64()}"
    @Shared def artifactory = ArtifactoryClientBuilder.create().setUrl(baseurl)
          .setUsername('admin').setPassword('password').build()

    def static final stageRepoKey = 'my-snapshot-local'
    def static final releaseRepoKey = 'my-release-local'
    def static final buildName = 'maven-example'
    def static final buildNumber = '37'

    def 'maven build promotion test'() {
        setup:
        // Create stage repo
        def stageRepo = createLocalRepo(stageRepoKey)
        // Create release repo
        def releaseRepo = createLocalRepo(releaseRepoKey)
        // Set build properties
        def buildProperties = [
                "build.number": buildNumber,
                "build.name": buildName,
                "build.timestamp": "1505520473765"
        ]

        when:
        // Upload build artifacts
        deploy(stageRepo, 'org/jfrog/test/multi/3.7-SNAPSHOT', 'multi-3.7-SNAPSHOT.pom', buildProperties)
        deploy(stageRepo, 'org/jfrog/test/multi1/3.7-SNAPSHOT', 'multi1-3.7-SNAPSHOT.jar', buildProperties)
        deploy(stageRepo, 'org/jfrog/test/multi1/3.7-SNAPSHOT', 'multi1-3.7-SNAPSHOT.pom', buildProperties)
        deploy(stageRepo, 'org/jfrog/test/multi2/3.7-SNAPSHOT', 'multi2-3.7-SNAPSHOT.jar', buildProperties)
        deploy(stageRepo, 'org/jfrog/test/multi2/3.7-SNAPSHOT', 'multi2-3.7-SNAPSHOT.pom', buildProperties)
        deploy(stageRepo, 'org/jfrog/test/multi3/3.7-SNAPSHOT', 'multi3-3.7-SNAPSHOT.war', buildProperties)
        deploy(stageRepo, 'org/jfrog/test/multi3/3.7-SNAPSHOT', 'multi3-3.7-SNAPSHOT.pom', buildProperties)
        // Upload build info
        uploadBuildInfo('build.json')
        // Execute promotion
        executePromotion(buildName, buildNumber, releaseRepoKey)

        then:
        // Check promoted build was created
        getBuildInfo(buildName, buildNumber + "-r") != null
        // Check artifacts were promoted
        releaseRepo.file('org/jfrog/test/multi/3.7/multi-3.7.pom').info() != null
        releaseRepo.file('org/jfrog/test/multi1/3.7/multi1-3.7.jar').info() != null
        releaseRepo.file('org/jfrog/test/multi1/3.7/multi1-3.7.pom').info() != null
        releaseRepo.file('org/jfrog/test/multi2/3.7/multi2-3.7.jar').info() != null
        releaseRepo.file('org/jfrog/test/multi2/3.7/multi2-3.7.pom').info() != null
        releaseRepo.file('org/jfrog/test/multi3/3.7/multi3-3.7.war').info() != null
        releaseRepo.file('org/jfrog/test/multi3/3.7/multi3-3.7.pom').info() != null
        // Check versions were changed inside pom files
        getPomVersion(releaseRepo, 'org/jfrog/test/multi/3.7/multi-3.7.pom') == "3.7"
        getPomVersion(releaseRepo, 'org/jfrog/test/multi1/3.7/multi1-3.7.pom') == "3.7"
        getPomVersion(releaseRepo, 'org/jfrog/test/multi2/3.7/multi2-3.7.pom') == "3.7"
        getPomVersion(releaseRepo, 'org/jfrog/test/multi3/3.7/multi3-3.7.pom') == "3.7"

        cleanup:
        ignoringExceptions { artifactory.repository(stageRepoKey)?.delete() }
        ignoringExceptions { artifactory.repository(releaseRepoKey)?.delete() }
        ignoringExceptions { deleteBuildInfo(buildName, buildNumber) }
        ignoringExceptions { deleteBuildInfo(buildName, buildNumber + "-r") }
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
        File file = new File("./src/test/groovy/PromotionTest/$fileName")
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
        def buildInfoFile = new File("./src/test/groovy/PromotionTest/$fileName")
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
     * Execute plugin named promotion
     * @param buildName
     * @param buildNumber
     * @param targetRepo
     * @return
     */
    def executePromotion(def buildName, def buildNumber, def targetRepo) {
        def conn = new URL("${baseurl}/api/plugins/build/promote/snapshotToRelease/$buildName/$buildNumber?params=snapExp=SNAPSHOT%7CtargetRepository=$targetRepo").openConnection()
        conn.requestMethod = 'POST'
        conn.setRequestProperty('Authorization', auth)
        assert conn.responseCode == 200
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
     * Get Build Info
     * @param buildName
     * @param buildNumber
     * @return
     */
    def getBuildInfo(def buildName, def buildNumber) {
        def conn = new URL("${baseurl}/api/build/$buildName/$buildNumber").openConnection()
        conn.requestMethod = 'GET'
        conn.setRequestProperty('Authorization', auth)
        assert conn.responseCode == 200
        def buildInfo = new JsonSlurper().parse(conn.inputStream)
        println "BuildInfo: $buildInfo"
        conn.disconnect()
        buildInfo
    }

    /**
     * Get pom version
     * @param repo
     * @param pomPath
     * @return
     */
    def getPomVersion(def repo, def pomPath) {
        def pom = getPom(repo, pomPath)
        if(!pom.version.isEmpty()) {
            return pom.version
        } else {
            return pom.parent.version
        }
    }

    /**
     * get Pom content
     * @param repo
     * @param pomPath
     * @return
     */
    def getPom(def repo, def pomPath) {
        new XmlSlurper(false, false).parse(repo.download(pomPath).doDownload())
    }

    def ignoringExceptions = { method ->
        try {
            method()
        } catch (Exception e) {
            e.printStackTrace()
        }
    }
}
