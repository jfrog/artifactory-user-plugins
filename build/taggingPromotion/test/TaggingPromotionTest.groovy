import groovyx.net.http.ContentType
import groovyx.net.http.HTTPBuilder
import groovyx.net.http.HttpResponseException
import groovyx.net.http.Method
import org.jfrog.artifactory.client.model.repository.settings.impl.MavenRepositorySettingsImpl
import spock.lang.Shared
import spock.lang.Specification

import javax.xml.ws.http.HTTPBinding

import org.jfrog.artifactory.client.ArtifactoryClientBuilder

/*
 * Copyright (C) 2017 JFrog Ltd.
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
class TaggingPromotionTest extends Specification {

    static final baseurl = 'http://localhost:8088/artifactory'
    static final user = 'admin'
    static final password = 'password'
    static final userPassword = "$user:$password"
    static final auth = "Basic ${userPassword.bytes.encodeBase64()}"

    static final devRepoKey = 'dev-local'
    static final promoRepoKey = 'release-local'
    static final warFilePath = 'org/jfrog/test/my-project/1.0.0/my-project-1.0.0.war'
    static final buildName = 'my-project'
    static final buildNumber = 1
    static final promotionName = 'cloudPromote'

    @Shared artifactory = ArtifactoryClientBuilder.create().setUrl(baseurl).setUsername(user).setPassword(password).build()

    def setupSpec() {
        // Create dev repo
        def devRepo = createLocalMavenRepo(devRepoKey)
        // Create promotion target repo
        createLocalMavenRepo(promoRepoKey)
        // Upload war file
        uploadWarFile(devRepo, warFilePath, buildName, buildNumber)
        // Upload build info
        addBuild('build.json')
    }

    def cleanupSpec() {
        // Delete build
        deleteAllBuilds(buildName)
        // Delete repositories
        deleteRepo(devRepoKey)
        deleteRepo(promoRepoKey)
    }

    def 'Promote to staging test'() {
        when:
        // Execute promotion
        promoteBuild(promotionName, buildName, buildNumber as String, ['staging':'true', 'targetRepository':promoRepoKey])
        // Get promoted artifact info
        def promotedWar = getArtifact(promoRepoKey, warFilePath)
        promotedWar.info()

        then:
        // Check artifact available in promotion repo
        noExceptionThrown()
        // Check artifact tagged properly
        promotedWar.getPropertyValues('aol.staging')[0] == 'true'
    }

    def 'Promote to oss test'() {
        when:
        // Execute promotion
        promoteBuild(promotionName, buildName, buildNumber as String, ['oss':'true', 'targetRepository':promoRepoKey])
        // Get promoted artifact info
        def promotedWar = getArtifact(promoRepoKey, warFilePath)
        promotedWar.info()

        then:
        // Check artifact available in promotion repo
        noExceptionThrown()
        // Check artifact tagged properly
        promotedWar.getPropertyValues('aol.oss')[0] == 'true'
    }

    def 'Promote to production test'() {
        when:
        // Execute promotion
        promoteBuild(promotionName, buildName, buildNumber as String, ['prod':'true', 'targetRepository':promoRepoKey])
        // Get promoted artifact info
        def promotedWar = getArtifact(promoRepoKey, warFilePath)
        promotedWar.info()

        then:
        // Check artifact available in promotion repo
        noExceptionThrown()
        // Check artifact tagged properly
        promotedWar.getPropertyValues('aol.prod')[0] == 'true'
    }

    def createLocalMavenRepo(String key) {
        def builder = artifactory.repositories().builders().localRepositoryBuilder()
                .key(key)
                .repositorySettings(new MavenRepositorySettingsImpl())
        artifactory.repositories().create(0, builder.build())
        return artifactory.repository(key)
    }

    def uploadWarFile(repo, path, buildName, buildNumber) {
        def warFileContent = 'war content does not matter'
        repo.upload(path, new ByteArrayInputStream(warFileContent.getBytes('UTF-8')))
                .withProperty('build.name', buildName)
                .withProperty('build.number', buildNumber as String)
                .doUpload()
    }

    def addBuild(file) {
        def conn = new URL("${baseurl}/api/build").openConnection()
        conn.requestMethod = 'PUT'
        conn.doOutput = true
        conn.setRequestProperty('Authorization', auth)
        conn.setRequestProperty('Content-Type', 'application/json')
        def jsonfile = new File("./src/test/groovy/TaggingPromotionTest/${file}")
        jsonfile.withInputStream { conn.outputStream << it }
        assert conn.responseCode == 204
        conn.disconnect()
    }

    def promoteBuild(promotionName, buildName, buildNumber, params = [:]) {
        def path = "$baseurl/api/plugins/build/promote/$promotionName/$buildName/$buildNumber"
        if(params) {
            path = "$path?params="
            params.each {
                path = "$path${it.key}=${it.value}" + '%7C'
            }
            // Remove pipe at the end of path
            path = path[0..-4]
        }

        def conn = new URL(path).openConnection()
        conn.requestMethod = 'POST'
        conn.setRequestProperty('Authorization', auth)
        assert conn.responseCode == 200
        conn.disconnect()
    }

    def getArtifact(repoKey, path) {
        return artifactory.repositories().repository(repoKey).file(path)
    }

    def deleteRepo(repoKey) {
        artifactory.repositories().repository(repoKey)?.delete()
    }

    def deleteAllBuilds(buildName) {
        def conn = new URL("${baseurl}/api/build/${buildName}?deleteAll=1").openConnection()
        conn.requestMethod = 'DELETE'
        conn.setRequestProperty('Authorization', auth)
        assert conn.responseCode == 200
        conn.disconnect()
    }

}