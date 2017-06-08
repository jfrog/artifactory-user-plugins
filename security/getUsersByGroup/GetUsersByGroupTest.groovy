import spock.lang.Specification
import org.jfrog.artifactory.client.model.Group
import static org.jfrog.artifactory.client.ArtifactoryClient.create
import org.jfrog.artifactory.client.model.builder.UserBuilder

class GetUsersByGroupTest extends Specification {
    def 'get users by group test'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = create(baseurl, 'admin', 'password')
        def auth = "Basic ${'admin:password'.bytes.encodeBase64()}"

        when:
        UserBuilder userBuilder = artifactory.security().builders().userBuilder()
        def user = userBuilder.name("user")
        .email("user@jfrog.com")
        .admin(true)
        .profileUpdatable(true)
        .password("password")
        .build();
        artifactory.security().createOrUpdate(user)

        Group group = artifactory.security().builders().groupBuilder()
        .name("group")
        .autoJoin(true)
        .description("new group")
        .build();
        artifactory.security().createOrUpdateGroup(group);

        def conn = new URL(baseurl + '/api/plugins/execute/getUsersByGroup?params=group=group').openConnection()
        conn.setRequestMethod('GET')
        conn.doOutput = true
        conn.setRequestProperty('Authorization', auth)
        assert conn.responseCode == 200
        def output = conn.getInputStream().text
        conn.disconnect()

        then:
        output.contains('\"group\"')

        cleanup:
        String result = artifactory.security().deleteUser("user")
        artifactory.security().deleteGroup("group")
    }
}
