import spock.lang.Specification

import org.jfrog.artifactory.client.model.repository.settings.impl.MavenRepositorySettingsImpl

import static org.jfrog.artifactory.client.ArtifactoryClient.create

class ArchiveOldArtifactsTest extends Specification {
    def 'archive old artifacts plugin test'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = create(baseurl, 'admin', 'password')

        def builder = artifactory.repositories().builders()
        def local1 = builder.localRepositoryBuilder().key('maven-local')
        .repositorySettings(new MavenRepositorySettingsImpl()).build()
        artifactory.repositories().create(0, local1)

        def local2 = builder.localRepositoryBuilder().key('maven-archive')
        .repositorySettings(new MavenRepositorySettingsImpl()).build()
        artifactory.repositories().create(0, local2)

        def stream = new ByteArrayInputStream('test'.getBytes('utf-8'))
        artifactory.repository('maven-local').upload('foo.txt', stream).doUpload()
        artifactory.repository('maven-local').file('foo.txt').properties().addProperty('archive', 'yes').doSet()

        when:
        def pt1 = "http://localhost:8088/artifactory/api/plugins/execute/archive_old_artifacts"
        def pt2 = "params=includePropertySet=archive%7CsrcRepo=maven-local%7CarchiveRepo=maven-archive"
        "curl -X POST -uadmin:password $pt1?$pt2".execute().waitFor()

        then:
        artifactory.repository('maven-archive').file('foo.txt').getPropertyValues('archived.timestamp')

        cleanup:
        artifactory.repository('maven-local').delete()
        artifactory.repository('maven-archive').delete()
    }
}
