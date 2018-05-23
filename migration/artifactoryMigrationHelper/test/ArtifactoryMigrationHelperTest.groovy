import org.jfrog.artifactory.client.ArtifactoryRequest
import org.jfrog.artifactory.client.impl.ArtifactoryRequestImpl
import org.jfrog.artifactory.client.model.impl.PackageTypeImpl
import org.jfrog.artifactory.client.model.repository.settings.impl.DockerRepositorySettingsImpl
import org.jfrog.artifactory.client.model.repository.settings.impl.MavenRepositorySettingsImpl
import org.jfrog.artifactory.client.model.repository.settings.impl.YumRepositorySettingsImpl
import spock.lang.Specification
import spock.lang.Shared
import groovy.json.JsonSlurper

import org.jfrog.artifactory.client.ArtifactoryClientBuilder

class ArtifactoryMigrationHelperTest extends Specification {
    public static final String MAVEN_LOCAL_KEY = 'libs-release-local'
    public static final String MAVEN_REMOTE_KEY = 'jcenter'
    public static final String MAVEN_VIRTUAL_KEY = 'libs-release'
    public static final String DOCKER_LOCAL_KEY = 'docker-local'
    public static final String RPM_LOCAL_KEY = 'rpm-local'

    static final sourceBaseurl = 'http://localhost:8088/artifactory'
    @Shared sourceArtifactory = ArtifactoryClientBuilder.create().setUrl(sourceBaseurl)
            .setUsername('admin').setPassword('password').build()

    static final targetBaseurl = 'http://localhost:8081/artifactory'
    @Shared targetArtifactory = ArtifactoryClientBuilder.create().setUrl(targetBaseurl)
            .setUsername('admin').setPassword('password').build()

    def 'maven repo migration test'() {
        setup:
        // Create maven local repo
        def mavenLocal = createLocalRepo(sourceArtifactory, MAVEN_LOCAL_KEY, new MavenRepositorySettingsImpl())
        // Create maven remote repo
        def mavenRemote = createRemoteRepo(sourceArtifactory, MAVEN_REMOTE_KEY, new MavenRepositorySettingsImpl(), 'https://jcenter.bintray.com')
        // Create maven virtual repo
        def mavenVirtual = createVirtualRepo(sourceArtifactory, MAVEN_VIRTUAL_KEY, new MavenRepositorySettingsImpl(), [MAVEN_LOCAL_KEY, MAVEN_REMOTE_KEY])


        // Execute plugin
        when:
        def message = sourceArtifactory.plugins().execute('artifactoryMigrationSetup').sync()
        then:
        message == 'Setup completed successfully'

        // Validate maven repos were created at target
        when:
        def targetMavenLocal = getRepo(targetArtifactory, MAVEN_LOCAL_KEY)
        def targetMavenRemote = getRepo(targetArtifactory, MAVEN_REMOTE_KEY)
        def targetMavenVirtual = getRepo(targetArtifactory, MAVEN_VIRTUAL_KEY)
        then:
        targetMavenLocal != null
        targetMavenLocal.repositorySettings.packageType == PackageTypeImpl.maven
        targetMavenRemote != null
        targetMavenVirtual != null

        // Check local maven repo replication was set
        when:
        def mavenLocalReplications = getRepoReplication(sourceArtifactory, MAVEN_LOCAL_KEY)
        then:
        mavenLocalReplications.size() == 1
        mavenLocalReplications[0].url.endsWith("artifactory/$MAVEN_LOCAL_KEY")

        cleanup:
        // Remove repos
        deleteRepo(sourceArtifactory, MAVEN_LOCAL_KEY)
        deleteRepo(sourceArtifactory, MAVEN_REMOTE_KEY)
        deleteRepo(sourceArtifactory, MAVEN_VIRTUAL_KEY)

        deleteRepo(targetArtifactory, MAVEN_LOCAL_KEY)
        deleteRepo(targetArtifactory, MAVEN_REMOTE_KEY)
        deleteRepo(targetArtifactory, MAVEN_VIRTUAL_KEY)
    }

    def 'docker repo migration test'() {
        setup:
        // Create docker local repo
        def dockerLocal = createLocalRepo(sourceArtifactory, DOCKER_LOCAL_KEY, new DockerRepositorySettingsImpl())

        // Execute plugin
        when:
        def message = sourceArtifactory.plugins().execute('artifactoryMigrationSetup').sync()
        then:
        message == 'Setup completed successfully'

        // Check docker repo was created at target
        when:
        def targetDockerLocal = getRepo(targetArtifactory, DOCKER_LOCAL_KEY)
        then:
        targetDockerLocal != null
        targetDockerLocal.repositorySettings.packageType == PackageTypeImpl.docker

        // Check docker repo replication was set
        when:
        def dockerLocalReplications = getRepoReplication(sourceArtifactory, DOCKER_LOCAL_KEY)
        then:
        dockerLocalReplications.size() == 1
        dockerLocalReplications[0].url.endsWith("artifactory/$DOCKER_LOCAL_KEY")

        cleanup:
        // Remove repos
        deleteRepo(sourceArtifactory, DOCKER_LOCAL_KEY)
        deleteRepo(targetArtifactory, DOCKER_LOCAL_KEY)
    }

    def 'rpm repo migration test'() {
        setup:
        // Create rpm package
        def rpmLocal = createLocalRepo(sourceArtifactory, RPM_LOCAL_KEY, new YumRepositorySettingsImpl())

        // Execute plugin
        when:
        def message = sourceArtifactory.plugins().execute('artifactoryMigrationSetup').sync()
        then:
        message == 'Setup completed successfully'

        // Check rpm repo was created at target
        when:
        def targetRpmLocal = getRepo(targetArtifactory, RPM_LOCAL_KEY)
        then:
        targetRpmLocal != null

        // Check rpm repo replication was set
        when:
        def rpmLocalReplications = getRepoReplication(sourceArtifactory, RPM_LOCAL_KEY)
        then:
        rpmLocalReplications.size() == 1
        rpmLocalReplications[0].url.endsWith("artifactory/$RPM_LOCAL_KEY")

        cleanup:
        // Remove repos
        deleteRepo(sourceArtifactory, RPM_LOCAL_KEY)
        deleteRepo(targetArtifactory, RPM_LOCAL_KEY)
    }

    def createLocalRepo(artifactory, repoKey, repositorySettings) {
        def builder = artifactory.repositories().builders()
        def repo = builder.localRepositoryBuilder().key(repoKey)
                .repositorySettings(repositorySettings)
                .build()
        artifactory.repositories().create(0, repo)
    }

    def createRemoteRepo(artifactory, repoKey, repositorySettings, url) {
        def builder = artifactory.repositories().builders()
        def repo = builder.remoteRepositoryBuilder().key(repoKey)
                .repositorySettings(repositorySettings)
                .url(url)
                .build()
        artifactory.repositories().create(0, repo)
    }

    def createVirtualRepo(artifactory, repoKey, repositorySettings, includedRepositories) {
        def builder = artifactory.repositories().builders()
        def repo = builder.virtualRepositoryBuilder().key(repoKey)
                .repositorySettings(repositorySettings)
                .repositories(includedRepositories)
                .build()
        artifactory.repositories().create(0, repo)
    }

    def deleteRepo(artifactory, key) {
        def repo = artifactory.repository(key)
        repo?.delete()
    }

    def getRepo(artifactory, key) {
        return artifactory.repository(key).get()
    }

    def getRepoReplication(artifactory, key) {
        ArtifactoryRequest getReplications = new ArtifactoryRequestImpl()
            .apiUrl("api/replications/$key")
            .method(ArtifactoryRequest.Method.GET)
            .responseType(ArtifactoryRequest.ContentType.JSON)
        return new JsonSlurper().parseText(artifactory.restCall(getReplications).getRawBody())
    }
}
