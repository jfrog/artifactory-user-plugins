import spock.lang.Specification

import static org.jfrog.artifactory.client.ArtifactoryClient.create

class ArchiveOldArtifactsTest extends Specification {
    def 'archive old artifacts plugin test'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = create(baseurl, 'admin', 'password')
        def stream = new ByteArrayInputStream('test'.getBytes('utf-8'))
        artifactory.repository('libs-release-local').upload('foo.txt', stream).doUpload()
        artifactory.repository('libs-release-local').file('foo.txt').properties().addProperty('archive', 'yes').doSet()

        when:
        def pt1 = "http://localhost:8088/artifactory/api/plugins/execute/archive_old_artifacts"
        def pt2 = "params=includePropertySet=archive|srcRepo=libs-release-local|archiveRepo=plugins-release-local"
        "curl -X POST -uadmin:password $pt1?$pt2".execute().waitFor()

        then:
        artifactory.repository('plugins-release-local').file('foo.txt').getPropertyValues('archived.timestamp')

        cleanup:
        artifactory.repository('plugins-release-local').delete('foo.txt')
    }
}
