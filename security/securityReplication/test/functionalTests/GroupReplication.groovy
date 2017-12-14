package SecurityReplicationTest
import artifactory.SecurityTestApi
import common.ArtGroups
import spock.lang.Requires
import spock.lang.IgnoreIf
import spock.lang.Stepwise
/**
 * Created by stanleyf on 30/07/2017.
 */
@Stepwise

class GroupReplication extends BaseSpec {
    final Integer REPLICATION_DELAY = 30000; // plugin wake up timer is every 60 seconds for replication to complete - securityreplication.json is 30 second
    def masterHA = super.masterHA
    def node1HA = super.node1HA
    def node2Pro = super.node2Pro
    def  IgnoreTestGroupReplicationFlagFlag = 1

    /*
    static def final testGroupReplication() {
        //need gradle build to support passing system properties to test methods
        //System.properties['testGroupReplication'] != 'true'
        return IgnoreTestGroupReplicationFlagFlag == 1
    }
    */

    def "Delete Default groups and verify deletion replicated across all the nodes" () {

        if (IgnoreTestGroupReplicationFlagFlag) return

        setup:
        SecurityTestApi sa = new SecurityTestApi(masterHA)
        ArtGroups helper = new ArtGroups()

        when: "Delete default group from master"
        sa.deleteDefaultGroup()

        then: "Verify default group is deleted from master node"
        helper.verifyNoDefaultGroups(masterHA)
        sleep(2*REPLICATION_DELAY)

        and: "Verify default groups are deleted from node1 from the replication "
        helper.verifyNoDefaultGroups(node1HA)

        and: "Verify default groups are deleted from node2 from the replication "
        helper.verifyNoDefaultGroups(node2Pro)
    }


    def "Create default groups after it was deleted"() {
        if (IgnoreTestGroupReplicationFlagFlag) return
        setup:
        SecurityTestApi sa = new SecurityTestApi(masterHA)
        ArtGroups helper = new ArtGroups()

        when: "Create default group from master"
        sa.createDefaultGroups()

        then: "Verify default group is deleted from master node"
        helper.verifyDefaultGroups(masterHA)
        sleep(REPLICATION_DELAY)

        and: "Verify default groups are deleted from node1 from the replication "
        helper.verifyDefaultGroups(node1HA)

        and: "Verify default groups are deleted from node2 from the replication "
        helper.verifyDefaultGroups(node2Pro)
    }


    def "Create 100 groups on Master node and verify replicated to the other node "() {
        if (IgnoreTestGroupReplicationFlagFlag) return
        setup:
        final count = 100
        SecurityTestApi ma = new SecurityTestApi(masterHA)
        ArtGroups helper = new ArtGroups()
        int beforeGC = ma.getGroupsList().size()

        when: "Create 100 groups on master"
        ma.createDynamicGroupList(count, "masterg", 1, "jfrog")
        sleep(2*REPLICATION_DELAY)

        then: "Verify group count on master "
        ma.getGroupsList().size() == beforeGC + count
        sleep(REPLICATION_DELAY)

        and: "verify group count on node 1"
        helper.getGroupCount(node1HA) == beforeGC + count

        and: "verify group count on node2"
        helper.getGroupCount(node2Pro)
    }


    def "Create 100 groups on node1 and verify replicated to the other node "() {
        if (IgnoreTestGroupReplicationFlagFlag) return
        setup:
        final count = 100
        SecurityTestApi n1 = new SecurityTestApi(node1HA)
        ArtGroups helper = new ArtGroups()
        int beforeGC = n1.getGroupsList().size()

        when: "Create 100 groups on master"
        n1.createDynamicGroupList(count, "node1g", 1, "jfrog")
        sleep(2*REPLICATION_DELAY)

        then: "Verify group count on node1 "
        n1.getGroupsList().size() == beforeGC + count
        sleep(REPLICATION_DELAY)

        and: "verify group count on node 1"
        helper.getGroupCount(masterHA) == beforeGC + count

        and: "verify group count on node2"
        helper.getGroupCount(node2Pro)
    }
}
