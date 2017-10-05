package SecurityReplicationTest.artifactory

import devenv.ArtifactoryManager
import org.jfrog.artifactory.client.Artifactory
import org.junit.FixMethodOrder
import org.junit.runners.MethodSorters

/**
 * Created by stanleyf on 11/20/16.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class SecurityTestApiTest extends GroovyTestCase {

    def artifactory = []
    Artifactory art
    SecurityTestApi sa

    void setUp() {
        super.setUp()
        artifactory = new ArtifactoryManager().getArtifactoryInstances() as Artifactory[]
        art = artifactory[0] as Artifactory
        sa = new SecurityTestApi(art)
    }

    void tearDown() {

    }

    void test01_CreateDefaultGroups() {
        println "Test: create default group list on " + art.getUri()
        sa.createDefaultGroups()
    }

    void test02_DeleteGroups() {
        println "Test: delete default groups on " + art.getUri()
        sa.deleteDefaultGroup()
    }

    void test03_CreateDynmicGroups() {
        println "Test: create n groups on " + art.getUri()
        sa.createDynamicGroupList(10, "soldev", 1, "password")
    }

    void test04_DeleteAllGroups() {
        println "Test: create n groups on " + art.getUri()
        sa.deleteAllGroups()
    }

    void test05_getAllGroups () {
        println "Test: print all groups on " + art.getUri()
        List<String> allGroups = sa.getGroupsList()
        println "Number of Groups: " + allGroups.size()
        println allGroups
    }

    void test10_CreateDefaultUserList() {
        println "Test: create default user list on " +  art.getUri()
        sa.createDefaultGroups()
        sa.createDefaultUserList()
    }

    void test11_DeleteDefaultUserList() {
        println "Test: delete default user list from " + art.getUri()
        sa.deleteDefaultUser()
    }

    void test13_DeleteAllUsers () {
        println "Test: delete all users on " + art.getUri()
        sa.deleteAllUsers()
    }

    void test14_getAllUsers () {
        println "Test: get all users on " + art.getUri()
        List<String> allUsers = sa.getAllUsers()
        println "Number of Users: " + allUsers.size()
        println allUsers
    }

    void test15_createDynamicUsers() {
        println "Test: create n users on " +  art.getUri()
        sa.createDynamicUserList(10, "soldev", 1, "password")
    }

    void test16_deleteUser() {
        println "Test: delete a users on " +  art.getUri()
        sa.createDefaultUserList()
        sa.deleteUser ("eytanh")
    }

    void test17_revokeUserApiKey () {
        println "Test: revoke an user API Keys " +  art.getUri()
        sa.createUserApiKey("qa1", "jfrog")
        sa.revokeUserApiKey("qa1")
    }

    void test18_revokeAllApiKeys () {
        println "Test: revoke all API Keys " +  art.getUri()
        sa.revokeAllApiKeys()
    }

    void test19_createUserApiKey () {
        println "Test: generate an user API Keys " +  art.getUri()
        println "API Key returned: " + sa.createUserApiKey("qa2", "jfrog")
    }

    void test20_regenerateApiKey () {
        println "Test: generate an user API Keys " +  art.getUri()
        println "API Key returned: " + sa.regenerateUserApiKey("qa2", "jfrog")
    }

    void test21_getUserApiKey () {
        println "Test: get user API Keys " +  art.getUri()
        println "API Key returned: " + sa.getUserApiKey("qa2", "jfrog")
    }

    void test30_createPermissionDefault () {
        println "Test: create permission for default users " +  art.getUri()
        sa.createPermissionDefault()
    }

    void test31_deletePermissionDefault () {
        println "Test: delete permission for default users " +  art.getUri()
        sa.deleteDefaultPermission()
    }

    void test32_readPermissionLIst () {
        println "Test: read  permission list " +  art.getUri()
        println sa.getPermissionList()
    }

    static def List randomNumber (int max, int loop) {
        Random rand = new Random()
        def randomIntegerList = []
        (1..loop).each {
            randomIntegerList << rand.nextInt(max+1)
        }
        return randomIntegerList;
    }
}
