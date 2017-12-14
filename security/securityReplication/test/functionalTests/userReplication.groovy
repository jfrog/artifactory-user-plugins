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
    final Integer REPLICATION_DELAY = 30000; // plugin wake up timer is every 60 seconds for replication to complete - securityreplication.json is 30 second
    def masterHA = super.masterHA
    def node1HA = super.node1HA
    def node2Pro = super.node2Pro

    def "Delete Default users and verify deletion replicated across all the nodes"() {
        setup:
        SecurityTestApi sa = new SecurityTestApi(masterHA)
        ArtUsers helper = new ArtUsers()

        sa.createDefaultUserList()
        sleep(REPLICATION_DELAY)

        when: "Delete default users from master node"
        sa.deleteDefaultUser()

        then: "Verify default users are deleted from master node"
        helper.verifyNoDefaultUsers(masterHA)
        sleep(REPLICATION_DELAY)

        and: "Verify default users are deleted from node1 from the replication"
        helper.verifyNoDefaultUsers(node1HA)

        and: "Verify default users are deleted from node2 from the replication"
        helper.verifyNoDefaultUsers(node2Pro)
    }

    def "Create default Users after it was deleted"() {
        setup:
        SecurityTestApi sa = new SecurityTestApi(masterHA)
        ArtUsers helper = new ArtUsers()

        when: "Create default users on master"
        sa.createDefaultUserList()

        then: "Verify default users are created on master node"
        helper.verifyDefaultUsers(masterHA)
        sleep(REPLICATION_DELAY)

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
        sleep(REPLICATION_DELAY)

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

        then: "verify user count created on the node 1"
        sleep(REPLICATION_DELAY)
        n1.getAllUsers().size() == n1.getAllUsers().size()
        sleep(REPLICATION_DELAY)

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
        sleep(REPLICATION_DELAY)

        then: "verify created APIKKey accessible for each default user in all artifactory nodes"
        helper.verifyApiKeyDefaultUser(masterHA, node1HA, node2Pro)
    }

    def "Replicate re-generated APIKeys for each Default User on Master to the other nodes" () {
        setup:
        SecurityTestApi ma = new SecurityTestApi(masterHA)
        ArtUsers helper = new ArtUsers()

        when: "Create APIKeys for each of the default users"
        helper.regenerateApiKeyDefaultUser(masterHA)
        sleep(REPLICATION_DELAY)

        then: "verify created APIKKey accessible for each default user in all artifactory nodes"
        helper.verifyApiKeyDefaultUser(masterHA, node1HA, node2Pro)
    }

    def "Replicate re-generated APIKeys for each Default User on Node 2 to the other nodes" () {
        setup:
        SecurityTestApi ma = new SecurityTestApi(node2Pro)
        ArtUsers helper = new ArtUsers()

        when: "Create APIKeys for each of the default users"
        helper.regenerateApiKeyDefaultUser(masterHA)
        sleep(REPLICATION_DELAY)

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
        sleep(REPLICATION_DELAY)

        when: "create 100 users in each node in parallel"
        def master = ma.createDynamicUserList(count, "masterp", 1, "jfrog")
        def node1 = n1.createDynamicUserList(count, "node1p", 1, "jfrog")
        def node2 = n2.createDynamicUserList(count, "node2p", 1, "jfrog")
        sleep(REPLICATION_DELAY)

        then: "verify user count on master"
        ma.getAllUsers().size() == beforeUC + 3*count

        then: "verify user count on both master and node 1"
        ma.getAllUsers().size() == n1.getAllUsers().size()

        and: "verify user count on both master and node 2"
        ma.getAllUsers().size() == n2.getAllUsers().size()
    }

}
