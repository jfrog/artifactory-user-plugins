import spock.lang.Specification
import org.jfrog.artifactory.client.model.Group
import org.jfrog.artifactory.client.ArtifactoryClientBuilder
import org.jfrog.artifactory.client.model.builder.UserBuilder
import groovy.json.JsonBuilder

class GetUsersByGroupTest extends Specification {
    def 'get users by group test'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = ArtifactoryClientBuilder.create().setUrl(baseurl)
            .setUsername('admin').setPassword('password').build()
        def auth = "Basic ${'admin:password'.bytes.encodeBase64()}"

        when:

        Group group = artifactory.security().builders().groupBuilder()
        .name("group")
        .autoJoin(true)
        .description("new group")
        .build()
        artifactory.security().createOrUpdateGroup(group)

        UserBuilder userBuilder = artifactory.security().builders().userBuilder()
        def user = userBuilder.name("user")
        .email("user@jfrog.com")
        .admin(true)
        .profileUpdatable(true)
        .password("password")
        .addGroup("group")
        .build()
        artifactory.security().createOrUpdate(user)

        def conn = new URL(baseurl + '/api/plugins/execute/getUsersByGroup?params=group=group').openConnection()
        conn.setRequestMethod('GET')
        conn.doOutput = true
        conn.setRequestProperty('Authorization', auth)
        assert conn.responseCode == 200
        def output = conn.getInputStream().text
        conn.disconnect()

        then:
        output.contains('\"user\"')

        cleanup:
        String result = artifactory.security().deleteUser("user")
        artifactory.security().deleteGroup("group")
    }
}
