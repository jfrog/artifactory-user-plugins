import org.jfrog.artifactory.client.model.repository.settings.impl.MavenRepositorySettingsImpl
import spock.lang.Specification

import org.jfrog.artifactory.client.ArtifactoryClientBuilder

class PreventXrayRejectedTest extends Specification {
    def 'prevent Xray rejected test'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = ArtifactoryClientBuilder.create().setUrl(baseurl)
            .setUsername('admin').setPassword('password').build()
        def auth = "Basic ${'admin:password'.bytes.encodeBase64()}"
        def jarfile = new File('./src/test/groovy/PreventXrayRejectedTest/lib-aopalliance-1.0.jar')

        def builder = artifactory.repositories().builders()
        def local = builder.localRepositoryBuilder().key('maven-test-local')
                .repositorySettings(new MavenRepositorySettingsImpl()).build()
        artifactory.repositories().create(0, local)

        artifactory.repository("maven-test-local")
                .upload("lib-aopalliance-1.0.jar", jarfile)
                .withProperty("xray.0000XSNJG0MQJHBF4QX1EFD6Y3.index.status", "Scanned")
                .withProperty("xray.xray.0000XSNJG0MQJHBF4QX1EFD6Y3.alert.componentId", "generic://sha256:62dc4f50804c40f3233977e2d3d1a4abca60bc41/lib-aopalliance-1.0.jar")
                .withProperty("xray.0000XSNJG0MQJHBF4QX1EFD6Y3.index.lastUpdated", "1503705604265")
                .withProperty("xray.0000XSNJG0MQJHBF4QX1EFD6Y3.alert.lastUpdated", "1503705604265")
                .withProperty("xray.0000XSNJG0MQJHBF4QX1EFD6Y3.alert.topSeverity", "Minor")
                .withProperty("sha256", "fdb7caded3b97e34ae35613f4eea509f38b5d011bcca2606f3f13f6552690e3d")
                .doUpload()

        when:
        def filPath="lib-aopalliance-1.0.jar"
        def conn = new URL(baseurl + '/maven-test-local/lib-aopalliance-1.0.jar').openConnection()
        conn.setRequestProperty('Authorization', auth)
        def responseCode = conn.responseCode

        then:
        responseCode == 403

        cleanup:
        artifactory.repository("maven-test-local").delete()
    }
}
