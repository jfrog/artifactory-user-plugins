import groovyx.net.http.HttpResponseException
import spock.lang.Specification

import static org.jfrog.artifactory.client.ArtifactoryClient.create

class PommerTest extends Specification {
    def 'pommer plugin test'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = create(baseurl, 'admin', 'password')
        def repo = artifactory.repository('plugins-release-local')
        def filepath = "foo/bar/baz/1.0/baz-1.0.txt"
        def pompath = "foo/bar/baz/1.0/baz-1.0.pom"

        when:
        def filecontents = new ByteArrayInputStream(filepath.bytes)
        repo.upload(filepath, filecontents).doUpload()
        repo.file(pompath).info()

        then:
        notThrown(HttpResponseException)

        when:
        repo.delete(pompath)
        repo.file(pompath).info()

        then:
        thrown(HttpResponseException)

        when:
        def plugin = artifactory.plugins().execute('pommify')
        plugin.withParameter('repos', 'plugins-release-local').sync()
        repo.file(pompath).info()

        then:
        notThrown(HttpResponseException)

        cleanup:
        repo.delete('foo')
    }
}
