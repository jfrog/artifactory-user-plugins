import spock.lang.Specification
import org.apache.http.client.HttpResponseException

import org.jfrog.artifactory.client.ArtifactoryClientBuilder
import org.jfrog.artifactory.client.model.repository.settings.impl.MavenRepositorySettingsImpl
import org.jfrog.artifactory.client.model.builder.UserBuilder

class PreventOverrideTest extends Specification {
    def 'prevent override test'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory1 = ArtifactoryClientBuilder.create().setUrl(baseurl).setUsername('admin').setPassword('password').build()

        UserBuilder userBuilder = artifactory1.security().builders().userBuilder()
        def user1 = userBuilder.name("non-admin")
        .email("newuser@jfrog.com")
        .admin(false)
        .profileUpdatable(true)
        .password("password")
        .build();
        artifactory1.security().createOrUpdate(user1)

        def artifactory2 = ArtifactoryClientBuilder.create().setUrl(baseurl)
                .setUsername('non-admin').setPassword('password').build()
        def xmlfile = new File('./src/test/groovy/PreventOverrideTest/maven-metadata.xml')

        def builder = artifactory1.repositories().builders()
        def local = builder.localRepositoryBuilder().key('maven-local')
        .repositorySettings(new MavenRepositorySettingsImpl()).build()
        artifactory1.repositories().create(0, local)

        when:
        artifactory2.repository("maven-local")
        .upload("maven-metadata.xml", xmlfile)
        .withProperty("prop", "test")
        .doUpload()

        then:
        thrown(HttpResponseException)

        cleanup:
        String result = artifactory1.security().deleteUser("non-admin")
        artifactory1.repository('maven-local').delete()
    }
}
