import groovy.json.JsonBuilder
import spock.lang.Specification
import org.jfrog.artifactory.client.model.repository.settings.impl.MavenRepositorySettingsImpl
import org.apache.http.client.HttpResponseException

import org.jfrog.artifactory.client.ArtifactoryClientBuilder

class ProtectPropertiesEditTest extends Specification {
    def 'property edit protect test'() {
        setup:
        def baseurl = "http://localhost:8088/artifactory"
        def auth = "Basic ${'admin:password'.bytes.encodeBase64().toString()}"
        def artifactory = ArtifactoryClientBuilder.create().setUrl(baseurl)
            .setUsername('admin').setPassword('password').build()
        def artuser = ArtifactoryClientBuilder.create().setUrl(baseurl)
            .setUsername('newuser').setPassword('password').build()
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
        def file1 = artifactory.repository("maven-local").file('tmpfile')
        file1.properties().addProperty('prop1', 'value').doSet()
        file1.deleteProperty('prop1')

        then:
        notThrown(HttpResponseException)

        when:
        def file2 = artuser.repository("maven-local").file('tmpfile')
        file2.properties().addProperty('prop2', 'value').doSet()
        file2.deleteProperty('prop2')

        then:
        thrown(HttpResponseException)

        cleanup:
        artifactory.repository("maven-local").delete()
        conn = new URL(baseurl + '/api/security/users/newuser').openConnection()
        conn.setRequestMethod('DELETE')
        conn.setRequestProperty('Authorization', auth)
        conn.getResponseCode()
    }
}
