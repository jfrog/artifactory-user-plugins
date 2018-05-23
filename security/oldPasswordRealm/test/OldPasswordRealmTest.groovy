import org.jfrog.artifactory.client.model.builder.UserBuilder
import org.jfrog.artifactory.client.model.repository.settings.impl.MavenRepositorySettingsImpl
import spock.lang.Specification

import org.jfrog.artifactory.client.ArtifactoryClientBuilder

class OldPasswordRealmTest extends Specification {
    def 'Old Password realm test'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = ArtifactoryClientBuilder.create().setUrl(baseurl)
            .setUsername('admin').setPassword('password').build()
        def auth = "Basic ${'test4:{DESede}XTBBQeBllBafJBccuRMdMw=='.bytes.encodeBase64()}"

        def jarfile = new File('./src/test/groovy/OldPasswordRealmTest/lib-aopalliance-1.0.jar')

        def builder = artifactory.repositories().builders()
        def local = builder.localRepositoryBuilder().key('maven-local')
                .repositorySettings(new MavenRepositorySettingsImpl()).build()
        artifactory.repositories().create(0, local)
        artifactory.repository("maven-local")
                .upload("lib-aopalliance-1.0.jar", jarfile)
                .doUpload()

        when:
        UserBuilder userBuilder = artifactory.security().builders().userBuilder()
        def user = userBuilder.name("test4")
                .email("test4@jfrog.com")
                .admin(true)
                .profileUpdatable(true)
                .password("test")
                .build()
        artifactory.security().createOrUpdate(user)

        def conn = new URL("${baseurl}/maven-local/lib-aopalliance-1.0.jar").openConnection()
        conn.requestMethod = 'GET'
        conn.setRequestProperty('Authorization', auth)

        then:
        assert conn.responseCode == 200
        conn.disconnect()

        cleanup:
        artifactory.security().deleteUser("test4")
        artifactory.repository("maven-local").delete()
    }
}
