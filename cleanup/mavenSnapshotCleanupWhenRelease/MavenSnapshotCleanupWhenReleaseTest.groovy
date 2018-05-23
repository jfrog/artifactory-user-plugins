import org.jfrog.artifactory.client.ArtifactoryClientBuilder

import org.jfrog.artifactory.client.Artifactory
import org.jfrog.artifactory.client.model.impl.SnapshotVersionBehaviorImpl
import org.jfrog.artifactory.client.model.repository.settings.impl.MavenRepositorySettingsImpl

import org.apache.http.client.HttpResponseException
import spock.lang.Specification

class MavenSnapshotCleanupWhenReleaseTest extends Specification {

    def TEST_FOLDER = 'org/jfrog/test'
    def TEST_NAME = 'test-snapshot-cleanup-when-release'
    def TEST_REPO_RELEASES = 'maven-local-releases'
    def TEST_REPO_SNAPSHOTS = 'maven-local-snaphots'

    private void createClientAndMavenRepo(Artifactory artifactory, String repoName, boolean handleReleases, boolean handleSnapshots){
        createClientAndMavenRepo(artifactory, repoName, handleReleases, handleSnapshots, false)
    }

    private void createClientAndMavenRepo(Artifactory artifactory, String repoName, boolean handleReleases, boolean handleSnapshots, boolean suppressPomConsistency){
        def builder = artifactory.repositories().builders()
        def repoSettings = new MavenRepositorySettingsImpl()
        repoSettings.handleReleases = handleReleases
        repoSettings.handleSnapshots = handleSnapshots
        repoSettings.suppressPomConsistencyChecks = suppressPomConsistency
        if (handleSnapshots){
            repoSettings.snapshotVersionBehavior = SnapshotVersionBehaviorImpl.unique
        }

        def repository = builder.localRepositoryBuilder().key(repoName)
            .repositorySettings(repoSettings).build()

        artifactory.repositories().create(0, repository)
    }


    def 'Maven Snapshot Cleanup When Release promotion move same repo test'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = ArtifactoryClientBuilder.create().setUrl(baseurl)
            .setUsername('admin').setPassword('password').build()
        createClientAndMavenRepo(artifactory, 'maven-local', true, true, true)

        artifactory.plugins().execute('mavenSnapshotCleanupWhenReleaseConfig').
            withParameter('action', 'reset').withParameter('repositories', '[["maven-local","maven-local"]]').sync()

        when:
        // Double upload to generate -2 snapshot
        uploadMavenArtifact(artifactory, 'maven-local', '1.0.0-SNAPSHOT')
        def snapName = uploadMavenArtifact(artifactory, 'maven-local', '1.0.0-SNAPSHOT')
        artifactory.repository('maven-local').file(TEST_FOLDER + '/' + TEST_NAME + '/1.0.0-SNAPSHOT/' + snapName + '.jar').move('maven-local', TEST_FOLDER + '/' + TEST_NAME + '/1.0.0/'+TEST_NAME + '-1.0.0.jar')
        artifactory.repository('maven-local').file(TEST_FOLDER + '/' + TEST_NAME + '/1.0.0-SNAPSHOT/' + snapName + '.pom').move('maven-local', TEST_FOLDER + '/' + TEST_NAME + '/1.0.0/'+TEST_NAME + '-1.0.0.pom')

        artifactory.repository('maven-local').file(TEST_FOLDER + '/' + TEST_NAME + '/1.0.0-SNAPSHOT').info()

        then:
        thrown(HttpResponseException)

        when:
        def metaData = artifactory.repository('maven-local').download(TEST_FOLDER + '/' + TEST_NAME + '/maven-metadata.xml').doDownload().text
        assert !metaData.contains('1.0.0-SNAPSHOT')
        assert metaData.contains('1.0.0')

        then:
        notThrown(HttpResponseException)

        cleanup:
        artifactory.repository('maven-local').delete()
    }

    def 'Maven Snapshot Cleanup When Release promotion move two repo test'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = ArtifactoryClientBuilder.create().setUrl(baseurl)
            .setUsername('admin').setPassword('password').build()
        createClientAndMavenRepo(artifactory, TEST_REPO_RELEASES, true, false, true)
        createClientAndMavenRepo(artifactory, TEST_REPO_SNAPSHOTS, false, true)
        artifactory.plugins().execute('mavenSnapshotCleanupWhenReleaseConfig').
            withParameter('action', 'reset').withParameter('repositories', '[["'+TEST_REPO_RELEASES+'","'+TEST_REPO_SNAPSHOTS+'"]]').sync()

        when:
        def snapName = uploadMavenArtifact(artifactory, TEST_REPO_SNAPSHOTS, '1.0.0-SNAPSHOT')
        artifactory.repository(TEST_REPO_SNAPSHOTS).file(TEST_FOLDER + '/' + TEST_NAME + '/1.0.0-SNAPSHOT/' + snapName + '.jar').move(TEST_REPO_RELEASES, TEST_FOLDER + '/' + TEST_NAME + '/1.0.0/'+TEST_NAME + '-1.0.0.jar')
        artifactory.repository(TEST_REPO_SNAPSHOTS).file(TEST_FOLDER + '/' + TEST_NAME + '/1.0.0-SNAPSHOT/' + snapName + '.pom').move(TEST_REPO_RELEASES, TEST_FOLDER + '/' + TEST_NAME + '/1.0.0/'+TEST_NAME + '-1.0.0.pom')

        artifactory.repository(TEST_REPO_SNAPSHOTS).file(TEST_FOLDER + '/' + TEST_NAME + '/1.0.0-SNAPSHOT').info()

        then:
        thrown(HttpResponseException)

        when:
        // Sometime metatData creation takes some time
        sleep(3000)
        def metaDataRelease = artifactory.repository(TEST_REPO_RELEASES).download(TEST_FOLDER + '/' + TEST_NAME + '/maven-metadata.xml').doDownload().text
        assert metaDataRelease.contains('1.0.0')

        then:
        notThrown(HttpResponseException)

        when:
        artifactory.repository(TEST_REPO_SNAPSHOTS).download(TEST_FOLDER + '/' + TEST_NAME + '/maven-metadata.xml').doDownload().text

        then:
        thrown(HttpResponseException)

        cleanup:
        artifactory.repository(TEST_REPO_RELEASES).delete()
        artifactory.repository(TEST_REPO_SNAPSHOTS).delete()
    }

    def 'Maven Snapshot Cleanup When Release disable test'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = ArtifactoryClientBuilder.create().setUrl(baseurl)
            .setUsername('admin').setPassword('password').build()
        createClientAndMavenRepo(artifactory, TEST_REPO_RELEASES, true, false)
        createClientAndMavenRepo(artifactory, TEST_REPO_SNAPSHOTS, false, true)
        artifactory.plugins().execute('mavenSnapshotCleanupWhenReleaseConfig').withParameter('action', 'reset').sync()

        when:
        def snapName = uploadMavenArtifact(artifactory, TEST_REPO_SNAPSHOTS, '1.1.0-SNAPSHOT')
        uploadMavenArtifact(artifactory, TEST_REPO_RELEASES, '1.1.0')
        artifactory.repository(TEST_REPO_SNAPSHOTS).file(TEST_FOLDER + '/' + TEST_NAME + '/1.1.0-SNAPSHOT/' + snapName + '.pom').info()

        then:
        notThrown(HttpResponseException)

        cleanup:
        artifactory.repository(TEST_REPO_RELEASES).delete()
        artifactory.repository(TEST_REPO_SNAPSHOTS).delete()
    }

    def 'Maven Snapshot Cleanup When Release same repo test'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = ArtifactoryClientBuilder.create().setUrl(baseurl)
            .setUsername('admin').setPassword('password').build()
        createClientAndMavenRepo(artifactory, 'maven-local', true, true)

        artifactory.plugins().execute('mavenSnapshotCleanupWhenReleaseConfig').
            withParameter('action', 'reset').withParameter('repositories', '[["maven-local","maven-local"]]').sync()

        def snapName = uploadMavenArtifact(artifactory, 'maven-local', '1.0.0-SNAPSHOT')
        uploadMavenArtifact(artifactory, 'maven-local', '1.1.0-SNAPSHOT')

        when:
        uploadMavenArtifact(artifactory, 'maven-local', '1.1.0')
        artifactory.repository('maven-local').file(TEST_FOLDER + '/' + TEST_NAME + '/1.1.0-SNAPSHOT').info()

        then:
        thrown(HttpResponseException)

        when:
        artifactory.repository('maven-local').file(TEST_FOLDER + '/' + TEST_NAME + '/1.0.0-SNAPSHOT').info()
        artifactory.repository('maven-local').file(TEST_FOLDER + '/' + TEST_NAME + '/1.0.0-SNAPSHOT/' + snapName  + '.pom').info()
        artifactory.repository('maven-local').file(TEST_FOLDER + '/' + TEST_NAME + '/1.0.0-SNAPSHOT/' + snapName  + '.jar').info()
        def metaData = artifactory.repository('maven-local').download(TEST_FOLDER + '/' + TEST_NAME + '/maven-metadata.xml').doDownload().text
        assert metaData.contains('1.0.0-SNAPSHOT')
        assert !metaData.contains('1.1.0-SNAPSHOT')

        then:
        notThrown(HttpResponseException)

        cleanup:
        artifactory.repository('maven-local').delete()
    }

    def 'Maven Snapshot Cleanup When Release classic test'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = ArtifactoryClientBuilder.create().setUrl(baseurl)
          .setUsername('admin').setPassword('password').build()
        createClientAndMavenRepo(artifactory, TEST_REPO_RELEASES, true, false)
        createClientAndMavenRepo(artifactory, TEST_REPO_SNAPSHOTS, false, true)
        artifactory.plugins().execute('mavenSnapshotCleanupWhenReleaseConfig').
            withParameter('action', 'reset').withParameter('repositories', '[["'+TEST_REPO_RELEASES+'","'+TEST_REPO_SNAPSHOTS+'"]]').sync()

        def snapName = uploadMavenArtifact(artifactory, TEST_REPO_SNAPSHOTS, '1.0.0-SNAPSHOT')
        uploadMavenArtifact(artifactory, TEST_REPO_SNAPSHOTS, '1.1.0-SNAPSHOT')

        when:
        uploadMavenArtifact(artifactory, TEST_REPO_RELEASES, '1.1.0')
        artifactory.repository(TEST_REPO_SNAPSHOTS).file(TEST_FOLDER + '/' + TEST_NAME + '/1.1.0-SNAPSHOT').info()

        then:
        thrown(HttpResponseException)

        when:
        artifactory.repository(TEST_REPO_SNAPSHOTS).file(TEST_FOLDER + '/' + TEST_NAME + '/1.0.0-SNAPSHOT').info()
        artifactory.repository(TEST_REPO_SNAPSHOTS).file(TEST_FOLDER + '/' + TEST_NAME + '/1.0.0-SNAPSHOT/' + snapName + '.pom').info()
        artifactory.repository(TEST_REPO_SNAPSHOTS).file(TEST_FOLDER + '/' + TEST_NAME + '/1.0.0-SNAPSHOT/' + snapName + '.jar').info()
        def metaData = artifactory.repository(TEST_REPO_SNAPSHOTS).download(TEST_FOLDER + '/' + TEST_NAME + '/maven-metadata.xml').doDownload().text
        assert metaData.contains('1.0.0-SNAPSHOT')
        assert !metaData.contains('1.1.0-SNAPSHOT')

        then:
        notThrown(HttpResponseException)

        cleanup:
        artifactory.repository(TEST_REPO_RELEASES).delete()
        artifactory.repository(TEST_REPO_SNAPSHOTS).delete()
    }


    // Return the file name without extention (could be modified when SNAPSHOT version due to Maven timestamp snapshot behavior)
    private def uploadMavenArtifact(Artifactory artifactory, String repo, String version){
        def pomContent = '<?xml version="1.0" encoding="UTF-8"?><project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd"><modelVersion>4.0.0</modelVersion><groupId>' + TEST_FOLDER.replace('/', '.') + '</groupId><artifactId>' + TEST_NAME + '</artifactId><version>'+version+'</version><packaging>jar</packaging></project>'
        def pom = new ByteArrayInputStream(pomContent.bytes)
        def jar = new ByteArrayInputStream('fake jar'.bytes)

        def artifactPathPrefix = TEST_FOLDER + '/' + TEST_NAME + '/' + version + '/' + TEST_NAME + '-' + version

        artifactory.repository(repo).upload(artifactPathPrefix + '.jar', jar).doUpload()
        org.jfrog.artifactory.client.model.File file = artifactory.repository(repo).upload(artifactPathPrefix + '.pom', pom).doUpload()

        return file.getName() - '.pom'
    }
}