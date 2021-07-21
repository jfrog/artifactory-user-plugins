import groovy.json.JsonSlurper
import spock.lang.Specification

import org.jfrog.artifactory.client.model.repository.settings.impl.MavenRepositorySettingsImpl

import org.jfrog.artifactory.client.ArtifactoryClientBuilder

class RepoStatsTest extends Specification {
    def 'multi-file repo stats test'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = ArtifactoryClientBuilder.create().setUrl(baseurl)
            .setUsername('admin').setPassword('password').build()

        def builder = artifactory.repositories().builders()
        def local = builder.localRepositoryBuilder().key('maven-local')
        .repositorySettings(new MavenRepositorySettingsImpl()).build()
        artifactory.repositories().create(0, local)

        def repo = artifactory.repository('maven-local')

        def strm1 = new ByteArrayInputStream('first text'.bytes)
        def strm2 = new ByteArrayInputStream('second text'.bytes)
        def strm3 = new ByteArrayInputStream('third text'.bytes)
        repo.upload('dir/path1/foo.txt', strm1).doUpload()
        repo.upload('dir/path1/bar.txt', strm2).doUpload()
        repo.upload('dir/path2/foo.txt', strm3).doUpload()

        when:
        def path1 = 'maven-local/dir/path1'
        def path2 = 'maven-local/dir/path2'
        def handle = artifactory.plugins().execute('repoStats')
        def response = handle.withParameter('paths', path1, path2).sync()
        def json = new JsonSlurper().parseText(response)

        then:
        json.stats[0].repoPath == 'maven-local/dir/path1'
        json.stats[0].count == 2
        json.stats[0].size == 21
        json.stats[0].sizeUnit == "bytes"
        json.stats[1].repoPath == 'maven-local/dir/path2'
        json.stats[1].count == 1
        json.stats[1].size == 10
        json.stats[1].sizeUnit == "bytes"

        cleanup:
        repo.delete()
    }
}
