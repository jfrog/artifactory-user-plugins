package artifactory

import devenv.ArtifactoryManager
import org.jfrog.artifactory.client.Artifactory
import org.junit.FixMethodOrder
import org.junit.runners.MethodSorters
import spock.lang.Stepwise


/**
 * Created by stanleyf on 11/19/16.
 */

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class RepositoryTestApiTest extends GroovyTestCase {

    def artifactory = []
    Artifactory art
    RepositoryTestApi repo

    void setUp() {
        super.setUp()
        artifactory = new ArtifactoryManager().getArtifactoryInstances() as Artifactory[]
        art = artifactory[0] as Artifactory
        repo = new RepositoryTestApi(artifactory[0])
    }

    void tearDown() {

    }

    void test1_CreateRepositoryLocal() {
        println "Test: create default local repository on " + art.uri
        repo.createRepositoryLocal()
    }

    void test2_GetLocalRepositories() {
        println "Test: display local repository created"
        repo.getRepositories("LOCAL").each { repo ->
            println repo.key;
        }
    }

    void test3_CreateRepositoryRemote() {
        println "Test: create default remote repository on " + art.uri
        repo.createRepositoriesRemote()
    }

    void test4_GetRemoteRepositories() {
        println "Test: display local repository created"
        repo.getRepositories("REMOTE").each { repo ->
            println repo.key;
        }
    }

    void test5_CreateRespositoryVirtual() {
        println "Test: create default virtual repository on " + art.uri
        repo.createRepositoriesVirtual()
    }

    void test6_DeleteRepositoryVirtual() {
        println "Test: delete default virtual repository on " + art.uri
        repo.deleteRepositoryVirtual()
    }

    void test7_DeleteRepositoryLocal() {
        println "Test: delete all default local repository on " + art.uri
        repo.deleteRepositoryLocal()
    }

    void test8_DeleteRepositoryRemote() {
        println "Test: delete all default remote repository on " + art.uri
        repo.deleteRepositoryRemote()
    }
}
