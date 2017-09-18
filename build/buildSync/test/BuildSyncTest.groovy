import spock.lang.Specification
import groovy.json.JsonSlurper
import org.jfrog.artifactory.client.ArtifactoryRequest
import org.jfrog.artifactory.client.impl.ArtifactoryRequestImpl

import static org.jfrog.artifactory.client.ArtifactoryClient.create

class BuildSyncTest extends Specification {
    def 'build sync test'() {
        setup:
        def baseurl1 = 'http://localhost:8088/artifactory'
        def artifactory1 = create(baseurl1, 'admin', 'password')
        def baseurl2 = 'http://localhost:8081/artifactory'
        def artifactory2 = create(baseurl2, 'admin', 'password')
        def userb = artifactory2.security().builders().userBuilder()
        def builder = userb.name('sync').email('sync@foo.bar').admin(true)
        builder.password('password')
        artifactory2.security().createOrUpdate(builder.build())

        when:
        def createreq = new ArtifactoryRequestImpl().apiUrl('api/build')
        createreq.method(ArtifactoryRequest.Method.PUT)
        createreq.requestType(ArtifactoryRequest.ContentType.JSON)
        def file = new File('./src/test/groovy/BuildSyncTest/build.json')
        createreq.requestBody(new JsonSlurper().parse(file))
        artifactory1.restCall(createreq)
        def handle = artifactory1.plugins().execute('buildSyncPushConfig')
        handle.withParameter('key', 'PushAllTo8080').sync()
        def checkreq = new ArtifactoryRequestImpl().apiUrl("api/build/test-build/1")
        checkreq.method(ArtifactoryRequest.Method.GET)
        checkreq.responseType(ArtifactoryRequest.ContentType.JSON)
        def response = artifactory2.restCall(checkreq)

        then:
        response.buildInfo.url == 'http://my-ci-server/jenkins/job/test-build/1/'

        cleanup:
        artifactory2.security().deleteUser('sync')
        def deletereq = new ArtifactoryRequestImpl().apiUrl('api/build/test-build')
        deletereq.method(ArtifactoryRequest.Method.DELETE)
        deletereq.setQueryParams(deleteAll: 1)
        artifactory2.restCall(deletereq)
        deletereq = new ArtifactoryRequestImpl().apiUrl('api/build/test-build')
        deletereq.method(ArtifactoryRequest.Method.DELETE)
        deletereq.setQueryParams(deleteAll: 1)
        artifactory1.restCall(deletereq)
    }
}
