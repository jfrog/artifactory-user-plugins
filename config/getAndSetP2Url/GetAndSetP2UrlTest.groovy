import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import spock.lang.Specification

class GetAndSetP2UrlTest extends Specification {
    def 'P2 URL get/set test'() {
        setup:
        def auth = "Basic ${'admin:password'.bytes.encodeBase64()}"
        def repourl = 'http://localhost:8088/artifactory/api/repositories/'
        def configs = [
            [key: 'p2-local', rclass: 'local', packageType: 'maven'],
            // [key: 'p2-remote', rclass: 'remote', packageType: 'p2',
            //  url: 'http://localhost:8088/artifactory/p2-local',
            //  username: 'admin', password: 'password'],
            [key: 'p2-virtual', rclass: 'virtual', packageType: 'p2']]
        for (conf in configs) {
            def conn = new URL("$repourl${conf['key']}").openConnection()
            conn.doOutput = true
            conn.requestMethod = 'PUT'
            conn.setRequestProperty('Authorization', auth)
            conn.setRequestProperty('Content-Type', 'application/json')
            conn.outputStream.bytes = new JsonBuilder(conf).toString().bytes
            assert conn.responseCode < 300
            conn.disconnect()
        }
        // TODO: this is a workaround. There is a property on remote P2
        // repositories that is added automatically if the repository is created
        // from the ui, but is not added if the REST api is used. The lack of
        // this property causes this user plugin to crash. Therefore, we use the
        // ui api to request this new repository.
        def tmpcfg =
            [type: "remoteRepoConfig", general: [repoKey: "p2-remote"], basic:
             [layout: "maven-2-default",
              url: "http://localhost:8088/artifactory/p2-local",
              includesPattern: "**/*", offline: false, contentSynchronisation:
              [enabled: true, properties: [enabled: false],
               statistics: [enabled: false], typeSpecific:
               [repoType: "P2", handleReleases: true, handleSnapshots: true,
                eagerlyFetchJars: false, eagerlyFetchSources: false,
                listRemoteFolderItems: true, rejectInvalidJars: false,
                maxUniqueSnapshots: 0, snapshotVersionBehavior: "UNIQUE",
                localChecksumPolicy: "CLIENT",
                remoteChecksumPolicy: "GEN_IF_ABSENT",
                pomCleanupPolicy: "discard_active_reference",
                suppressPomConsistencyChecks: true]]], advanced:
             [propertySets: [], storeArtifactsLocally: true,
              allowContentBrowsing: false, shareConfiguration: false,
              blackedOut: false, synchronizeArtifactProperties: false, cache:
              [keepUnusedArtifactsHours: 0, assumedOfflineLimitSecs: 300,
               retrievalCachePeriodSecs: 600,
               missedRetrievalCachePeriodSecs: 1800], network:
              [username: "admin", password: "password", syncProperties: false,
               lenientHostAuth: false, cookieManagement: false,
               socketTimeout: 15000]], typeSpecific:
             [repoType: "P2", handleReleases: true, handleSnapshots: true,
              eagerlyFetchJars: false, eagerlyFetchSources: false,
              listRemoteFolderItems: true, rejectInvalidJars: false,
              maxUniqueSnapshots: 0, snapshotVersionBehavior: "UNIQUE",
              localChecksumPolicy: "CLIENT",
              remoteChecksumPolicy: "GEN_IF_ABSENT",
              pomCleanupPolicy: "discard_active_reference",
              suppressPomConsistencyChecks: true]]
        def tmpurl = 'http://localhost:8088/artifactory/ui/admin/repositories'
        def connct = new URL(tmpurl).openConnection()
        connct.doOutput = true
        connct.requestMethod = 'POST'
        connct.setRequestProperty('Authorization', auth)
        connct.setRequestProperty('Content-Type', 'application/json')
        connct.outputStream.bytes = new JsonBuilder(tmpcfg).toString().bytes
        assert connct.responseCode < 300
        connct.disconnect()

        when:
        def exurl = 'http://localhost:8088/artifactory/api/plugins/execute/'
        def cnfg = [repo: 'p2-virtual',
                    urls: ['random', 'local://nonexistent', 'local://p2-local',
                           'http://localhost:8088/artifactory/p2-remote']]
        def cnct = new URL("${exurl}modifyP2Urls").openConnection()
        cnct.doOutput = true
        cnct.requestMethod = 'POST'
        cnct.setRequestProperty('Authorization', auth)
        cnct.setRequestProperty('Content-Type', 'application/json')
        cnct.outputStream.bytes = new JsonBuilder(cnfg).toString().bytes
        assert cnct.responseCode < 300
        def modresponse = new JsonSlurper().parse(cnct.inputStream)
        cnct.disconnect()
        def params = "?params=repo=p2-virtual"
        cnct = new URL("${exurl}getP2Urls$params").openConnection()
        cnct.requestMethod = 'POST'
        cnct.setRequestProperty('Authorization', auth)
        assert cnct.responseCode < 300
        def getresponse = new JsonSlurper().parse(cnct.inputStream)
        cnct.disconnect()

        then:
        modresponse.repo == 'p2-virtual'
        modresponse.valid == true
        modresponse.urls.size() == 2
        modresponse.urls.contains('local://p2-local')
        modresponse.urls.contains('http://localhost:8088/artifactory/p2-remote')
        getresponse.repo == modresponse.repo
        getresponse.valid == modresponse.valid
        getresponse.urls.size() == modresponse.urls.size()
        getresponse.urls.containsAll(modresponse.urls)

        cleanup:
        for (conf in configs) {
            def conn = new URL("$repourl${conf['key']}").openConnection()
            conn.requestMethod = 'DELETE'
            conn.setRequestProperty('Authorization', auth)
            conn.responseCode
            conn.disconnect()
        }
        // TODO: temporary workaround: remove later
        def connect = new URL("${repourl}p2-remote").openConnection()
        connect.requestMethod = 'DELETE'
        connect.setRequestProperty('Authorization', auth)
        connect.responseCode
        connect.disconnect()
    }
}
