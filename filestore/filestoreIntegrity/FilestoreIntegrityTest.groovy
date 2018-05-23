import spock.lang.Specification
import org.jfrog.lilypad.Control
import org.jfrog.artifactory.client.model.repository.settings.impl.MavenRepositorySettingsImpl
import org.jfrog.artifactory.client.ArtifactoryClientBuilder
import groovy.json.JsonSlurper

class FilestoreIntegrityTest extends Specification {
    def 'filestore integrity test'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = ArtifactoryClientBuilder.create().setUrl(baseurl)
            .setUsername('admin').setPassword('password').build()
        def auth = "Basic ${'admin:password'.bytes.encodeBase64().toString()}"

        def builder = artifactory.repositories().builders()
        def local = builder.localRepositoryBuilder().key('maven-local')
        .repositorySettings(new MavenRepositorySettingsImpl()).build()
        artifactory.repositories().create(0, local)

        artifactory.repository('maven-local').upload('foo/bar/file', new ByteArrayInputStream('test'.getBytes('utf-8'))).doUpload()
        String sha1 = artifactory.repository('maven-local').file('foo/bar/file').info().getChecksums().getSha1()
        String folder = sha1.take(2)
        String path = "/var/opt/jfrog/artifactory/data/filestore/${folder}/"
        Control.deleteFolder(8088, path + sha1)
        Control.createFolder(8088, path + 'changed')

        when:
        def conn = new URL(baseurl + '/api/plugins/execute/filestoreIntegrity').openConnection()
        conn.setRequestMethod('GET')
        conn.doOutput = true
        conn.setRequestProperty('Authorization', auth)
        conn.setRequestProperty('Content-Type', 'application/json')
        conn.getResponseCode()
        def output = new JsonSlurper().parse(conn.getInputStream())


        then:
        output.missing[0].repoPath == 'maven-local:foo/bar/file'
        output.missing[0].sha1 == sha1
        output.extra[0] == folder + '/changed'

        cleanup:
        artifactory.repository('maven-local').delete()
    }
}
