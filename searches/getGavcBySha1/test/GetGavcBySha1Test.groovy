import org.jfrog.artifactory.client.model.repository.settings.impl.MavenRepositorySettingsImpl
import spock.lang.Specification

import org.jfrog.artifactory.client.ArtifactoryClientBuilder

class GetGavcBySha1Test extends Specification {
    def 'get gavc by sha1 test'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def auth = "Basic ${'admin:password'.bytes.encodeBase64().toString()}"
        def artifactory = ArtifactoryClientBuilder.create().setUrl(baseurl)
            .setUsername('admin').setPassword('password').build()

        def builder = artifactory.repositories().builders()
        def local = builder.localRepositoryBuilder().key('maven-local')
                .repositorySettings(new MavenRepositorySettingsImpl()).build()
        artifactory.repositories().create(0, local)

        def jarfile = new File('./src/test/groovy/GetGavcBySha1Test/guava-18.0.jar')
        artifactory.repository('maven-local').upload('com/google/guava/guava/18.0/guava-18.0.jar', jarfile).doUpload()

        when:
        def conn = new URL(baseurl + '/api/plugins/execute/getGavcBySha1?params=sha1=cce0823396aa693798f8882e64213b1772032b09').openConnection()
        conn.setRequestProperty('Authorization', auth)
        conn.requestMethod = 'GET'
        def code = conn.getResponseCode()
        conn.disconnect()

        then:
        code == 200

        cleanup:
        artifactory.repository('maven-local').delete()
    }
}
