import spock.lang.Specification
import org.jfrog.artifactory.client.model.repository.settings.impl.MavenRepositorySettingsImpl

import org.jfrog.artifactory.client.ArtifactoryClientBuilder

class PreventCCRejectedTest extends Specification {
    def 'prevent cc rejected test'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = ArtifactoryClientBuilder.create().setUrl(baseurl)
            .setUsername('admin').setPassword('password').build()

        def builder = artifactory.repositories().builders()
        def local = builder.localRepositoryBuilder().key('maven-local')
        .repositorySettings(new MavenRepositorySettingsImpl()).build()
        artifactory.repositories().create(0, local)

        def file = new ByteArrayInputStream('sample file'.bytes)
        artifactory.repository("maven-local").upload('sample', file).doUpload()
        artifactory.repository("maven-local").file('sample').properties().addProperty('blackduck.cc.id', 'sample').doSet()

        when:
        artifactory.plugins().execute('setCCProperty').
            withParameter('id', 'sample').
            withParameter('appName', 'sampleapp').
            withParameter('appVersion', '3.14').
            withParameter('status', 'approved').sync()
        def conn = new URL(baseurl + '/maven-local/sample;buildInfo.governance.blackduck.appName=sampleapp;buildInfo.governance.blackduck.appVersion=3.14').openConnection()
        def code1 = conn.getResponseCode()

        then:
        code1 >= 200 && code1 < 300

        when:
        artifactory.plugins().execute('setCCProperty').
            withParameter('id', 'sample').
            withParameter('appName', 'sampleapp').
            withParameter('appVersion', '3.14').
            withParameter('status', 'rejected').sync()
        conn = new URL(baseurl + '/maven-local/sample;buildInfo.governance.blackduck.appName=sampleapp;buildInfo.governance.blackduck.appVersion=3.14').openConnection()
        def code2 = conn.getResponseCode()

        then:
        code2 == 403

        cleanup:
        artifactory.repository("maven-local").delete()
    }
}