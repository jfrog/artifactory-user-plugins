import groovy.json.JsonBuilder
import spock.lang.Specification
import org.jfrog.artifactory.client.model.repository.settings.impl.MavenRepositorySettingsImpl
import groovyx.net.http.HttpResponseException

import static org.jfrog.artifactory.client.ArtifactoryClient.create

class ProtectPropertiesEditTest extends Specification {
    def 'property edit protect test'() {
        setup:
        def baseurl = "http://localhost:8088/artifactory"
        def auth = "Basic ${'admin:password'.bytes.encodeBase64().toString()}"
        def artifactory = create(baseurl, "admin", "password")
        def artuser = create(baseurl, "newuser", "password")
        def artuser2 = create(baseurl, "newuser2", "password")
        def conn = new URL(baseurl + '/api/security/users/newuser').openConnection()
        def json = new JsonBuilder()
        json name: 'newuser', email: 'newuser@jfrog.com', password: "password", admin: true
        conn.setDoOutput(true)
        conn.setRequestMethod('PUT')
        conn.setRequestProperty('Authorization', auth)
        conn.getOutputStream().write(json.toString().bytes)
        conn.getResponseCode()
        def tmpfile = new ByteArrayInputStream('temporary file'.bytes)

        def builder = artifactory.repositories().builders()
        def local = builder.localRepositoryBuilder().key('maven-local')
        .repositorySettings(new MavenRepositorySettingsImpl()).build()
        artifactory.repositories().create(0, local)

        when:
        def file1 = artuser.repository("maven-local").file('tmpfile')
        //TODO: CHECK THAT FILE PROPERTY IS CREATED
        //TODO: CHECK THAT FILE CANNOT BE DELETED BY artuser2
        //TODO: CHECK THAT FILE CANNOT BE ADDED BY ARTUSER2 to namespace
        //TODO: CHECK THAT FILE CAN BE DELETED BY artuser

        then:
        notThrown(HttpResponseException)

        cleanup:
        artifactory.repository("maven-local").delete()
        conn = new URL(baseurl + '/api/security/users/newuser').openConnection()
        conn.setRequestMethod('DELETE')
        conn.setRequestProperty('Authorization', auth)
        conn.getResponseCode()
    }
}
