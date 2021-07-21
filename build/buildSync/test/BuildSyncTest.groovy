/*
 * Copyright (C) 2021 JFrog Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import spock.lang.Shared
import spock.lang.Specification
import groovy.json.JsonSlurper
import org.jfrog.artifactory.client.ArtifactoryRequest
import org.jfrog.artifactory.client.impl.ArtifactoryRequestImpl

import org.jfrog.artifactory.client.ArtifactoryClientBuilder

class BuildSyncTest extends Specification {

    @Shared def baseurl1 = 'http://localhost:8088/artifactory'
    @Shared def artifactory1 = ArtifactoryClientBuilder.create().setUrl(baseurl1).setUsername('admin').setPassword('password').build()
    @Shared def baseurl2 = 'http://localhost:8081/artifactory'
    @Shared def artifactory2 = ArtifactoryClientBuilder.create().setUrl(baseurl2).setUsername('admin').setPassword('password').build()

    def setupSpec() {
        // Create replication user
        def userb = artifactory2.security().builders().userBuilder()
        def builder = userb.name('sync').email('sync@foo.bar').admin(true)
        builder.password('password')
        artifactory2.security().createOrUpdate(builder.build())
    }

    def cleanupSpec() {
        // Delete replication user
        artifactory2.security().deleteUser('sync')
    }

    def 'build sync push test'() {
        setup:
        addBuild(artifactory1, './src/test/groovy/BuildSyncTest/test-build.json')
        when:
        runPushReplication(artifactory1, 'PushTo8081')
        def response = getBuild(artifactory2, 'test-build', '1')
        then:
        response.buildInfo.url == 'http://my-ci-server/jenkins/job/test-build/1/'
        cleanup:
        ignoringExceptions { deleteBuild(artifactory2, 'test-build') }
        ignoringExceptions { deleteBuild(artifactory1, 'test-build') }
    }

    def 'event build sync push test'() {
        setup:
        addBuild(artifactory1, './src/test/groovy/BuildSyncTest/event-test-build.json')
        when:
        def response = getBuild(artifactory2, 'event-test-build', '1')
        then:
        response.buildInfo.url == 'http://my-ci-server/jenkins/job/event-test-build/1/'
        cleanup:
        ignoringExceptions { deleteBuild(artifactory2, 'event-test-build') }
        ignoringExceptions { deleteBuild(artifactory1, 'event-test-build') }
    }

    def 'promotion sync push test'() {
        setup:
        addBuild(artifactory1, './src/test/groovy/BuildSyncTest/test-build.json')
        when:
        runPushReplication(artifactory1, 'PushTo8081WithPromotions')
        def response = getBuild(artifactory2, 'test-build', '1')
        then:
        // Check if build has been replicated
        response.buildInfo.url == 'http://my-ci-server/jenkins/job/test-build/1/'
        when:
        promoteBuild(artifactory1, 'test-build', '1', './src/test/groovy/BuildSyncTest/promotion.json')
        runPushReplication(artifactory1, 'PushTo8081WithPromotions')
        response = getBuild(artifactory2, 'test-build', '1')
        then:
        // Check if promotion has been replicated
        response.buildInfo.statuses[0].status == 'promoted'
        cleanup:
        ignoringExceptions { deleteBuild(artifactory2, 'test-build') }
        ignoringExceptions { deleteBuild(artifactory1, 'test-build') }
    }

    def 'build sync pull test'() {
        setup:
        addBuild(artifactory2, './src/test/groovy/BuildSyncTest/test-build.json')
        when:
        runPullReplication(artifactory1, 'PullFrom8081')
        def response = getBuild(artifactory1, 'test-build', '1')
        then:
        response.buildInfo.url == 'http://my-ci-server/jenkins/job/test-build/1/'
        cleanup:
        ignoringExceptions { deleteBuild(artifactory1, 'test-build') }
        ignoringExceptions { deleteBuild(artifactory2, 'test-build') }
    }

    def 'promotion sync pull test'() {
        setup:
        addBuild(artifactory2, './src/test/groovy/BuildSyncTest/test-build.json')
        when:
        runPullReplication(artifactory1, 'PullFrom8081WithPromotions')
        def response = getBuild(artifactory1, 'test-build', '1')
        then:
        response.buildInfo.url == 'http://my-ci-server/jenkins/job/test-build/1/'
        when:
        promoteBuild(artifactory2, 'test-build', '1', './src/test/groovy/BuildSyncTest/promotion.json')
        runPullReplication(artifactory1, 'PullFrom8081WithPromotions')
        response = getBuild(artifactory1, 'test-build', '1')
        then:
        response.buildInfo.statuses[0].status == 'promoted'
        cleanup:
        ignoringExceptions { deleteBuild(artifactory1, 'test-build') }
        ignoringExceptions { deleteBuild(artifactory2, 'test-build') }
    }

    def addBuild(artifactory, filePath) {
        def createreq = new ArtifactoryRequestImpl().apiUrl('api/build')
        createreq.method(ArtifactoryRequest.Method.PUT)
        createreq.requestType(ArtifactoryRequest.ContentType.JSON)
        def file = new File(filePath)
        createreq.requestBody(new JsonSlurper().parse(file))
        artifactory.restCall(createreq)
    }

    def runPushReplication(artifactory, key) {
        def handle = artifactory.plugins().execute('buildSyncPushConfig')
        handle.withParameter('key', key).sync()
    }

    def runPullReplication(artifactory, key) {
        def handle = artifactory.plugins().execute('buildSyncPullConfig')
        handle.withParameter('key', key).sync()
    }

    def getBuild(artifactory, buildName, buildNumber) {
        def checkreq = new ArtifactoryRequestImpl().apiUrl("api/build/$buildName/$buildNumber")
        checkreq.method(ArtifactoryRequest.Method.GET)
        checkreq.responseType(ArtifactoryRequest.ContentType.JSON)
        return new groovy.json.JsonSlurper().parseText( artifactory.restCall(checkreq).getRawBody())
    }

    def deleteBuild(artifactory, buildName) {
        def deletereq = new ArtifactoryRequestImpl().apiUrl("api/build/$buildName")
        deletereq.method(ArtifactoryRequest.Method.DELETE)
        deletereq.setQueryParams(deleteAll: "1")
        artifactory.restCall(deletereq)
    }

    def promoteBuild(artifactory, buildName, buildNumber, filePath){
        def createreq = new ArtifactoryRequestImpl().apiUrl("api/build/promote/$buildName/$buildNumber")
        createreq.method(ArtifactoryRequest.Method.POST)
        createreq.requestType(ArtifactoryRequest.ContentType.JSON)
        def file = new File(filePath)
        createreq.requestBody(new JsonSlurper().parse(file))
        artifactory.restCall(createreq)
    }

    def ignoringExceptions = { method ->
        try {
            method()
        } catch (Exception e) {
            e.printStackTrace()
        }
    }
}
