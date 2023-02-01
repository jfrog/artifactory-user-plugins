package SecurityReplicationTest

import artifactory.SecurityTestApi
import common.ArtUsers
import spock.lang.Ignore
import spock.lang.Stepwise

/**
 * Created by stanleyf on 27/07/2017.
 */
@Stepwise

class UserReplication extends BaseSpec {
    def masterHA = super.masterHA
    def node1HA = super.node1HA
    def node2Pro = super.node2Pro

    def "Delete all users and verify admin type users are not deleted across all nodes" () {
        setup:
        def xrayApiKeyma
        def xrayApiKeyn1
        def xrayApiKeyn2
        SecurityTestApi sa = new SecurityTestApi(masterHA)
        SecurityTestApi n1 = new SecurityTestApi(node1HA)
        SecurityTestApi n2 = new SecurityTestApi(node2Pro)
        ArtUsers helper = new ArtUsers()

        sa.createSingleUser("xray", "donotdeleteme-ma")
        n1.createSingleUser("xray", "donotdeleteme-n1")
        n2.createSingleUser("xray", "donotdeleteme-n2")
        sa.revokeAllApiKeys()
        n1.revokeAllApiKeys()
        n2.revokeAllApiKeys()
        xrayApiKeyma = sa.createUserApiKey("xray", "donotdeleteme-ma")
        xrayApiKeyn1 = n1.createUserApiKey("xray", "donotdeleteme-n1")
        xrayApiKeyn2 = n2.createUserApiKey("xray", "donotdeleteme-n2")

        when: "Delete all users from master node"
        sa.deleteAllUsers()
        super.runReplication()

        then: "Verify admin type users are not deleted from replication"
        helper.verifynoReplicateUsers (node1HA)
        helper.verifynoReplicateUsers (node2Pro)
        helper.verifynoReplicateUsers (masterHA)

        then: "Verify XRAY user api key was not replicated"
        xrayApiKeyma == sa.getUserApiKey("xray", "donotdeleteme-ma")
        xrayApiKeyn1 == n1.getUserApiKey("xray", "donotdeleteme-n1")
        xrayApiKeyn2 == n2.getUserApiKey("xray", "donotdeleteme-n2")
    }

    def "Delete Default users and verify deletion replicated across all the nodes"() {
        setup:
        SecurityTestApi sa = new SecurityTestApi(masterHA)
        ArtUsers helper = new ArtUsers()

        sa.createDefaultUserList()
        super.runReplication()

        when: "Delete default users from master node"
        sa.deleteDefaultUser()

        then: "Verify default users are deleted from master node"
        helper.verifyNoDefaultUsers(masterHA)
        super.runReplication()

        and: "Verify default users are deleted from node1 from the replication"
        helper.verifyNoDefaultUsers(node1HA)

        and: "Verify default users are deleted from node2 from the replication"
        helper.verifyNoDefaultUsers(node2Pro)

        and: "Verify admin type users are not deleted from replication"
    }

    def "Create default Users after it was deleted"() {
        setup:
        SecurityTestApi sa = new SecurityTestApi(masterHA)
        ArtUsers helper = new ArtUsers()

        when: "Create default users on master"
        sa.createDefaultUserList()

        then: "Verify default users are created on master node"
        helper.verifyDefaultUsers(masterHA)
        super.runReplication()

        and: "Verify default users are replicated on node 1"
        helper.verifyDefaultUsers(node1HA)

        and: "Verify default users are replicated on node 2"
        helper.verifyDefaultUsers(node2Pro)
    }

    def "Create 300 hundred users on master node and verify replicated to the other node "() {

        setup:
        final count = 300
        SecurityTestApi ma = new SecurityTestApi(masterHA)
        ArtUsers helper = new ArtUsers()
        int beforeUC = ma.getAllUsers().size()

        when: "Create 300 users on master "
        ma.createDynamicUserList(count, "master", 1, "jfrog")

        then: "verify user count created on the master node"
        ma.getAllUsers().size() == beforeUC + count
        super.runReplication()

        then: "verify user count on node 1"
        helper.getUserCount(node1HA) == beforeUC + count

        and: "verify user count on node 2"
        helper.getUserCount(node2Pro) == beforeUC + count
    }

    def "Create 300 users on Node 1 and verify replicated to the other nodes "() {
        setup:
        final count = 300
        SecurityTestApi n1 = new SecurityTestApi(node1HA)
        ArtUsers helper = new ArtUsers()

        when: "Create 300 users on slave node 1 "
        n1.createDynamicUserList(count, "node1", 1, "jfrog")
        super.runReplication()

        then: "verify user count on master node"
        helper.getUserCount(masterHA) == n1.getAllUsers().size()

        and: "verify user count on node 2"
        helper.getUserCount(node2Pro) == n1.getAllUsers().size()
    }

    def "Replicate create APIKeys for each Default User to other notes "() {
        setup:
        SecurityTestApi ma = new SecurityTestApi(masterHA)
        ArtUsers helper = new ArtUsers()
        ma.revokeAllApiKeys()

        when: "Create APIKeys for each of the default users"
        helper.createApiKeyDefaultUser(masterHA)
        super.runReplication()

        then: "verify created APIKKey accessible for each default user in all artifactory nodes"
        helper.verifyApiKeyDefaultUser(masterHA, node1HA, node2Pro)
    }

    def "Replicate re-generated APIKeys for each Default User on Master to the other nodes" () {
        setup:
        SecurityTestApi ma = new SecurityTestApi(masterHA)
        ArtUsers helper = new ArtUsers()

        when: "Create APIKeys for each of the default users"
        helper.regenerateApiKeyDefaultUser(masterHA)
        super.runReplication()

        then: "verify created APIKKey accessible for each default user in all artifactory nodes"
        helper.verifyApiKeyDefaultUser(masterHA, node1HA, node2Pro)
    }

    def "Replicate re-generated APIKeys for each Default User on Node 2 to the other nodes" () {
        setup:
        SecurityTestApi ma = new SecurityTestApi(node2Pro)
        ArtUsers helper = new ArtUsers()

        when: "Create APIKeys for each of the default users"
        helper.regenerateApiKeyDefaultUser(masterHA)
        super.runReplication()

        then: "verify created APIKKey accessible for each default user in all artifactory nodes"
        helper.verifyApiKeyDefaultUser(node2Pro, masterHA, node1HA)
    }

    def "Parallel create users on all nodes "() {
        def newMasterCount = 0
        setup:
        final count = 50
        SecurityTestApi ma = new SecurityTestApi(masterHA)
        SecurityTestApi n1 = new SecurityTestApi(node1HA)
        SecurityTestApi n2 = new SecurityTestApi(node2Pro)
        int beforeUC = ma.getAllUsers().size()
        super.runReplication()

        when: "create 100 users in each node in parallel"
        def master = ma.createDynamicUserList(count, "masterp", 1, "jfrog")
        def node1 = n1.createDynamicUserList(count, "node1p", 1, "jfrog")
        def node2 = n2.createDynamicUserList(count, "node2p", 1, "jfrog")
        super.runReplication()

        then: "verify user count on master"
        ma.getAllUsers().size() == beforeUC + 3*count

        then: "verify user count on both master and node 1"
        ma.getAllUsers().size() == n1.getAllUsers().size()

        and: "verify user count on both master and node 2"
        ma.getAllUsers().size() == n2.getAllUsers().size()
    }

    def "Verify auto created users exists and XRAY user credentials are not replicated after user tests" () {
        setup:
        SecurityTestApi sa = new SecurityTestApi(masterHA)
        SecurityTestApi n1 = new SecurityTestApi(node1HA)
        SecurityTestApi n2 = new SecurityTestApi(node2Pro)
        sa.revokeAllApiKeys()
        n1.revokeAllApiKeys()
        n2.revokeAllApiKeys()

        ArtUsers helper = new ArtUsers()
        when: "check admin user not replicated"

        then: "Verify admin type users are not deleted from replication"
        helper.verifynoReplicateUsers (node1HA)
        helper.verifynoReplicateUsers (node2Pro)
        helper.verifynoReplicateUsers (masterHA)

        then: "Verify XRAY password is not replicated"
        sa.getUserApiKey("xray", "donotdeleteme-ma") == null
        n1.getUserApiKey("xray", "donotdeleteme-n1") == null
        n2.getUserApiKey("xray", "donotdeleteme-n2") == null
    }
}

