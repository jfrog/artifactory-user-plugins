package SecurityReplicationTest

import artifactory.RepositoryTestApi
import artifactory.SecurityTestApi
import common.ArtUsers
import devenv.ArtifactoryManager
import groovy.json.JsonSlurper
import org.apache.http.client.HttpResponseException
import org.apache.http.HttpException
import org.jfrog.artifactory.client.ArtifactoryRequest
import org.jfrog.artifactory.client.impl.ArtifactoryRequestImpl
import spock.lang.Shared
import spock.lang.Specification
import org.jfrog.artifactory.client.Artifactory

/**
 * Created by stanleyf on 21/07/2017.
 */
abstract class BaseSpec extends Specification {

    static public Artifactory masterHA, node1HA, node2Pro
    static public artifactory = []
    boolean testGroupReplication = false
    boolean testPermissionReplication = false


    //
    // pre-popluate 3 artifactory instances with default set of Repositories, Groups, Permissions and Users.
    //
    def setupSpec() {
        when: "Get master HA, Slave HA and Pro Artifactory instances"
        setupArtifactory()

        then: "Check MasterHA up"
        masterHA.system().ping()

        and: "Check node 1 HA up"
        node1HA.system().ping()

        and: "Check node 2 Pro up"
        node2Pro.system().ping()

        and: "Check if security replication on Master is responding"
        getSecurityReplicationFilterParam(masterHA)

        when: "Populate Artifactory's with repositories for permission targets. "
        createRepositories(masterHA)
        createRepositories(node1HA)
        createRepositories(node2Pro)

        when: "Populate Artifactory's with default groups"
        createGroups(masterHA)
        createGroups(node1HA)
        createGroups(node2Pro)

        when: "Populate Artifactory's with Users"
        createUsers(masterHA)
        createUsers(node1HA)
        createUsers(node2Pro)

        then: "Create Permission Groups "
        createPermission(masterHA)
        createPermission(node1HA)
        createPermission(node2Pro)

        when: "Enable Security Replication Plugin - Test can begin"
        setupReplication()

    }

    def setupReplication () {
        try {
            masterHA.plugins().execute("distSecRep").sync()
        } catch (HttpException ex) {
            println "Test Failed - Distribution security request failed " + ex.message
            assert false: "No Tests executed"
        }
    }

    def runReplication() {
        def requestThreads = []
        artifactory.each { art ->
            Thread requestThread = new Thread({
                ArtifactoryRequest configRequest = new ArtifactoryRequestImpl().apiUrl("api/plugins/execute/testRunSecurityReplication")
                        .method(ArtifactoryRequest.Method.POST)
                        .responseType(ArtifactoryRequest.ContentType.TEXT)
                try {
                    println "Requesting replication at ${art.getUri()}"
                    def response = art.restCall(configRequest).toString()
                    println "Replication executed at ${art.getUri()}: $response"
                } catch (Exception ex) {
                    println "Failed to execute replication at ${art.getUri()}: ${ex.getMessage()}"
                }
            })
            requestThreads << requestThread
        }
        requestThreads.each { it.start() }
        requestThreads.each { it.join() }
    }

    def getSecurityReplicationFilterParam(Artifactory art) {
        def filter = 0

        ArtifactoryRequest configRequest = new ArtifactoryRequestImpl().apiUrl("api/plugins/execute/secRepJson")
            .method(ArtifactoryRequest.Method.GET)
            .responseType(ArtifactoryRequest.ContentType.TEXT)
        try {
             def response = new JsonSlurper().parseText(art.restCall(configRequest).getRawBody())
             filter = response.securityReplication.filter
        } catch (HttpResponseException hre) {
            System.out.println("Could not retrieve the security replication configuration from ${art.getUri()}. Status: ${hre.getStatusCode()}, ${hre.getMessage()}")
            assert false: "No Tests executed"
        }

        switch (filter) {
            case 1: println "Test Replicate only users"
                return true
            case 2: println "Test Replicate user and group";
                testGroupReplication = true
                System.setProperty('testGroupReplication', 'true')
                return true
            case 3: println "Test Replicate user, group and permissions";
                testGroupReplication = true
                testPermissionReplication = true
                System.setProperty('testGroupReplication', 'true')
                System.setProperty('testPermissionReplication', 'true')
                return true
            default: assert false: "Test Fail - Illegal filter value ${filter}"
        }
        return false
    }

    static def createGroups (Artifactory art) {
        SecurityTestApi sa = new SecurityTestApi(art)
        sa.createDefaultGroups()
    }

    static def createUsers (Artifactory art) {
        SecurityTestApi sa = new SecurityTestApi(art)
        sa.createDefaultUserList()
    }

    static def createRepositories (Artifactory art) {
        RepositoryTestApi repo
        repo = new RepositoryTestApi(art)
        repo.createRepositoryLocal()
        repo.createRepositoriesRemote()
        repo.createRepositoriesVirtual()
    }

    static def createPermission (Artifactory art) {
        SecurityTestApi per = new SecurityTestApi(art)
        per.createPermissionDefault()
    }


    def setupArtifactory () {
        artifactory = new ArtifactoryManager().getArtifactoryInstances() as Artifactory[]
        masterHA = artifactory[0] as Artifactory
        node1HA = artifactory[1] as Artifactory
        node2Pro = artifactory[2] as Artifactory
    }

/*
    def cleanupSpec() {
        setup:
        SecurityTestApi sa = new SecurityTestApi(masterHA)

        when:
        sa.deleteAllUsers()
        sa.deleteAllGroups()
    }
*/
}
