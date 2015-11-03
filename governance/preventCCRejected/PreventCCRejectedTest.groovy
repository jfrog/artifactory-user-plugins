import spock.lang.Specification

import static org.jfrog.artifactory.client.ArtifactoryClient.create

class PreventCCRejectedTest extends Specification {
    def 'prevent cc rejected test'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = create(baseurl, 'admin', 'password')
        def repo = artifactory.repository('libs-release-local')
        def file = new ByteArrayInputStream('sample file'.bytes)
        repo.upload('sample', file).doUpload()
        repo.file('sample').properties().addProperty('blackduck.cc.id', 'sample').doSet()

        when:
        artifactory.plugins().execute('setCCProperty').
            withParameter('id', 'sample').
            withParameter('appName', 'sampleapp').
            withParameter('appVersion', '3.14').
            withParameter('status', 'approved').sync()
        def conn = new URL(baseurl + '/libs-release-local/sample;buildInfo.governance.blackduck.appName=sampleapp;buildInfo.governance.blackduck.appVersion=3.14').openConnection()
        def code1 = conn.getResponseCode()

        then:
        code1 >= 200 && code1 < 300

        when:
        artifactory.plugins().execute('setCCProperty').
            withParameter('id', 'sample').
            withParameter('appName', 'sampleapp').
            withParameter('appVersion', '3.14').
            withParameter('status', 'rejected').sync()
        conn = new URL(baseurl + '/libs-release-local/sample;buildInfo.governance.blackduck.appName=sampleapp;buildInfo.governance.blackduck.appVersion=3.14').openConnection()
        def code2 = conn.getResponseCode()

        then:
        code2 == 403

        cleanup:
        repo.delete('sample')
    }
}
