import org.apache.http.client.HttpResponseException
import spock.lang.Specification

import org.jfrog.artifactory.client.model.repository.settings.impl.MavenRepositorySettingsImpl

import org.jfrog.artifactory.client.ArtifactoryClientBuilder

class PommerTest extends Specification {
    def 'pommer plugin test'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = ArtifactoryClientBuilder.create().setUrl(baseurl)
            .setUsername('admin').setPassword('password').build()

        def builder = artifactory.repositories().builders()
        def local = builder.localRepositoryBuilder().key('maven-local')
        .repositorySettings(new MavenRepositorySettingsImpl()).build()
        artifactory.repositories().create(0, local)

        def repo = artifactory.repository('maven-local')
        def filepath = "foo/bar/baz/1.0/baz-1.0.txt"
        def pompath = "foo/bar/baz/1.0/baz-1.0.pom"

        when:
        def filecontents = new ByteArrayInputStream(filepath.bytes)
        repo.upload(filepath, filecontents).doUpload()
        repo.file(pompath).info()

        then:
        notThrown(HttpResponseException)

        cleanup:
        repo.delete()
    }

    def 'pommer pommify execution test'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = ArtifactoryClientBuilder.create().setUrl(baseurl)
            .setUsername('admin').setPassword('password').build()

        def builder = artifactory.repositories().builders()
        def local = builder.localRepositoryBuilder().key('maven-local')
        .repositorySettings(new MavenRepositorySettingsImpl()).build()
        artifactory.repositories().create(0, local)

        def repo = artifactory.repository('maven-local')
        def filepath = "foo/bar/baz/1.0/baz-1.0.txt"
        def pompath = "foo/bar/baz/1.0/baz-1.0.pom"

        when:
        def filecontents = new ByteArrayInputStream(filepath.bytes)
        repo.upload(filepath, filecontents).doUpload()
        repo.delete(pompath)
        def plugin = artifactory.plugins().execute('pommify')
        plugin.withParameter('repos', 'maven-local').sync()
        repo.file(pompath).info()

        then:
        notThrown(HttpResponseException)

        cleanup:
        repo.delete()
    }
}
