import static org.jfrog.artifactory.client.ArtifactoryClient.create
import groovy.json.JsonSlurper
import spock.lang.Specification

class RepoStatsTest extends Specification {
    def 'multi-file repo stats test'() {
        setup:
        def artifactory = create("http://localhost:8088/artifactory", "admin", "password")
        artifactory.repository('libs-release-local').upload('dir/path1/foo.txt', new ByteArrayInputStream('first text'.getBytes('utf-8'))).doUpload()
        artifactory.repository('libs-release-local').upload('dir/path1/bar.txt', new ByteArrayInputStream('second text'.getBytes('utf-8'))).doUpload()
        artifactory.repository('libs-release-local').upload('dir/path2/foo.txt', new ByteArrayInputStream('third text'.getBytes('utf-8'))).doUpload()

        when:
        def pt1 = "http://localhost:8088/artifactory/api/plugins/execute/repoStats"
        def pt2 = "params=paths=libs-release-local/dir/path1,libs-release-local/dir/path2"
        def ex = "curl -X POST -uadmin:password $pt1?$pt2".execute()
        ex.waitFor()
        def json = new JsonSlurper().parse(ex.inputStream)

        then:
        json.stats[0].repoPath == 'libs-release-local/dir/path1'
        json.stats[0].count == 2
        json.stats[0].size == '21 bytes'
        json.stats[1].repoPath == 'libs-release-local/dir/path2'
        json.stats[1].count == 1
        json.stats[1].size == '10 bytes'

        cleanup:
        artifactory.repository('libs-release-local').delete('dir')
    }
}
