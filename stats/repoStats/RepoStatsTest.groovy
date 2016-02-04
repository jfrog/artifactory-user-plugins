import groovy.json.JsonSlurper
import spock.lang.Specification

import static org.jfrog.artifactory.client.ArtifactoryClient.create

class RepoStatsTest extends Specification {
    def 'multi-file repo stats test'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = create(baseurl, 'admin', 'password')
        def repo = artifactory.repository('libs-release-local')
        def strm1 = new ByteArrayInputStream('first text'.bytes)
        def strm2 = new ByteArrayInputStream('second text'.bytes)
        def strm3 = new ByteArrayInputStream('third text'.bytes)
        repo.upload('dir/path1/foo.txt', strm1).doUpload()
        repo.upload('dir/path1/bar.txt', strm2).doUpload()
        repo.upload('dir/path2/foo.txt', strm3).doUpload()

        when:
        def path1 = 'libs-release-local/dir/path1'
        def path2 = 'libs-release-local/dir/path2'
        def handle = artifactory.plugins().execute('repoStats')
        def response = handle.withParameter('paths', path1, path2).sync()
        def json = new JsonSlurper().parseText(response)

        then:
        json.stats[0].repoPath == 'libs-release-local/dir/path1'
        json.stats[0].count == 2
        json.stats[0].size == 21
        json.stats[0].sizeUnit == "bytes"
        json.stats[1].repoPath == 'libs-release-local/dir/path2'
        json.stats[1].count == 1
        json.stats[1].size == 10
        json.stats[1].sizeUnit == "bytes"

        cleanup:
        repo.delete('dir')
    }
}
