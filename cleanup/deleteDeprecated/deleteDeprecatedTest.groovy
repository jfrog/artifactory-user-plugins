import groovyx.net.http.HttpResponseException
import spock.lang.Specification

import static org.jfrog.artifactory.client.ArtifactoryClient.create

class DeleteDeprecatedTest extends Specification {
    def 'delete deprecated plugin test'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = create(baseurl, 'admin', 'password')
        artifactory.repository('libs-release-local').upload('some/path1/file1', new ByteArrayInputStream('test1'.getBytes('utf-8'))).doUpload()
        artifactory.repository('libs-release-local').upload('some/path2/file2', new ByteArrayInputStream('test2'.getBytes('utf-8'))).doUpload()
        artifactory.repository('libs-release-local').file('some/path2/file2').properties().addProperty('analysis.deprecated', 'true').doSet()

        when:
        "curl -X POST -uadmin:password http://localhost:8088/artifactory/api/plugins/execute/deleteDeprecatedPlugin".execute().waitFor()
        artifactory.repository('libs-release-local').file('some/path2/file2').info()

        then:
        thrown(HttpResponseException)
        artifactory.repository('libs-release-local').file('some/path1/file1').info()

        cleanup:
        artifactory.repository('libs-release-local').delete('some')
    }
}
