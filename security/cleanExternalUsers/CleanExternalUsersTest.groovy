import groovyx.net.http.HttpResponseException
import spock.lang.Specification

import static org.jfrog.artifactory.client.ArtifactoryClient.create
import org.jfrog.artifactory.client.model.builder.UserBuilder

class CleanExternalUsersTest extends Specification {
    def 'clean external users test'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = create(baseurl, 'admin', 'password')

        when:
        def userBuilder = artifactory.security().builders().userBuilder()
        def user = userBuilder.name("deleteme@foo.bar")
        user.email("deleteme@foo.bar").password("password")
        artifactory.security().createOrUpdate(user.build())
        artifactory.security().user("deleteme@foo.bar").email

        then:
        notThrown(HttpResponseException)

        when:
        artifactory.plugins().execute("cleanExternalUsers").sync()
        artifactory.security().user("deleteme@foo.bar").email

        then:
        thrown(HttpResponseException)
    }
}
