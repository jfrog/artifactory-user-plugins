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
        def file = new File('./src/test/groovy/BuildSyncTest/test-build.json')
        createreq.requestBody(new JsonSlurper().parse(file))
        artifactory1.restCall(createreq)
        def handle = artifactory1.plugins().execute('buildSyncPushConfig')
        handle.withParameter('key', 'PushTo8081').sync()
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
        ignoringExceptions { artifactory2.restCall(deletereq) }
        deletereq = new ArtifactoryRequestImpl().apiUrl('api/build/test-build')
        deletereq.method(ArtifactoryRequest.Method.DELETE)
        deletereq.setQueryParams(deleteAll: 1)
        ignoringExceptions { artifactory1.restCall(deletereq) }
    }

    def 'event build sync test'() {
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
        def file = new File('./src/test/groovy/BuildSyncTest/event-test-build.json')
        createreq.requestBody(new JsonSlurper().parse(file))
        artifactory1.restCall(createreq)

        def checkreq = new ArtifactoryRequestImpl().apiUrl("api/build/event-test-build/1")
        checkreq.method(ArtifactoryRequest.Method.GET)
        checkreq.responseType(ArtifactoryRequest.ContentType.JSON)
        def response = artifactory2.restCall(checkreq)

        then:
        response.buildInfo.url == 'http://my-ci-server/jenkins/job/event-test-build/1/'

        cleanup:
        artifactory2.security().deleteUser('sync')
        def deletereq = new ArtifactoryRequestImpl().apiUrl('api/build/event-test-build')
        deletereq.method(ArtifactoryRequest.Method.DELETE)
        deletereq.setQueryParams(deleteAll: 1)
        ignoringExceptions { artifactory2.restCall(deletereq) }
        deletereq = new ArtifactoryRequestImpl().apiUrl('api/build/event-test-build')
        deletereq.method(ArtifactoryRequest.Method.DELETE)
        deletereq.setQueryParams(deleteAll: 1)
        ignoringExceptions { artifactory1.restCall(deletereq) }
    }

    def 'promotion sync test'() {
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
        // Create build
        def createreq = new ArtifactoryRequestImpl().apiUrl('api/build')
        createreq.method(ArtifactoryRequest.Method.PUT)
        createreq.requestType(ArtifactoryRequest.ContentType.JSON)
        def file = new File('./src/test/groovy/BuildSyncTest/test-build.json')
        createreq.requestBody(new JsonSlurper().parse(file))
        artifactory1.restCall(createreq)
        // Run replication
        def handle = artifactory1.plugins().execute('buildSyncPushConfig')
        handle.withParameter('key', 'PushTo8081WithPromotions').sync()
        // Get build at destination
        def checkreq = new ArtifactoryRequestImpl().apiUrl("api/build/test-build/1")
        checkreq.method(ArtifactoryRequest.Method.GET)
        checkreq.responseType(ArtifactoryRequest.ContentType.JSON)
        def response = artifactory2.restCall(checkreq)

        then:
        // Check if build has been replicated
        response.buildInfo.url == 'http://my-ci-server/jenkins/job/test-build/1/'

        when:
        // Promote build
        createreq = new ArtifactoryRequestImpl().apiUrl('api/build/promote/test-build/1')
        createreq.method(ArtifactoryRequest.Method.POST)
        createreq.requestType(ArtifactoryRequest.ContentType.JSON)
        file = new File('./src/test/groovy/BuildSyncTest/promotion.json')
        createreq.requestBody(new JsonSlurper().parse(file))
        artifactory1.restCall(createreq)
        // Run replication
        handle = artifactory1.plugins().execute('buildSyncPushConfig')
        handle.withParameter('key', 'PushTo8081WithPromotions').sync()
        // Get build at destination
        checkreq = new ArtifactoryRequestImpl().apiUrl("api/build/test-build/1")
        checkreq.method(ArtifactoryRequest.Method.GET)
        checkreq.responseType(ArtifactoryRequest.ContentType.JSON)
        response = artifactory2.restCall(checkreq)

        then:
        // Check if promotion has been repicated
        response.buildInfo.statuses[0].status == 'promoted'

        cleanup:
        artifactory2.security().deleteUser('sync')
        def deletereq = new ArtifactoryRequestImpl().apiUrl('api/build/test-build')
        deletereq.method(ArtifactoryRequest.Method.DELETE)
        deletereq.setQueryParams(deleteAll: 1)
        ignoringExceptions { artifactory2.restCall(deletereq) }
        deletereq = new ArtifactoryRequestImpl().apiUrl('api/build/test-build')
        deletereq.method(ArtifactoryRequest.Method.DELETE)
        deletereq.setQueryParams(deleteAll: 1)
        ignoringExceptions { artifactory1.restCall(deletereq) }
    }

    def ignoringExceptions = { method ->
        try {
            method()
        } catch (Exception e) {
            e.printStackTrace()
        }
    }
}
