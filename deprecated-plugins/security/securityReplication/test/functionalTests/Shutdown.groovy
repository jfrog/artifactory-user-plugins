package SecurityReplicationTest

import artifactory.SecurityTestApi
import common.ArtUsers
import org.jfrog.lilypad.Control
import spock.lang.Stepwise

@Stepwise
class Shutdown extends BaseSpec {
    final SLEEP_TIME = 30000
    final count = 50

    def "Add users while master node is down"() {
        when:
        Control.stop('8088')
        def up = false
        def sa = new SecurityTestApi(node1HA)
        sa.createDynamicUserList(count, "node1", 150, "jfrog")
        super.runReplication()

        then:
        ArtUsers.getUserCount(node1HA) == ArtUsers.getUserCount(node2Pro)

        when:
        Control.resume('8088')
        up = true
        sleep(SLEEP_TIME)
        super.runReplication()

        then:
        ArtUsers.getUserCount(node1HA) == ArtUsers.getUserCount(masterHA)

        cleanup:
        if (!up) Control.resume('8088')
    }

    def "Add users while node 2 is down"() {
        when:
        Control.stop('8091')
        def up = false
        def sa = new SecurityTestApi(masterHA)
        sa.createDynamicUserList(count, "master", 300, "jfrog")
        super.runReplication()

        then:
        ArtUsers.getUserCount(masterHA) == ArtUsers.getUserCount(node1HA)

        when:
        Control.resume('8091')
        up = true
        sleep(SLEEP_TIME)
        super.runReplication()

        then:
        ArtUsers.getUserCount(masterHA) == ArtUsers.getUserCount(node2Pro)

        cleanup:
        if (!up) Control.resume('8091')
    }

    def "Sync users while master node is down"() {
        when:
        Control.stop('8088')
        def up = false
        def sa1 = new SecurityTestApi(node1HA)
        def sa2 = new SecurityTestApi(node2Pro)
        def sr1 = sa1.createDynamicUserList(25, "test1", 300, "jfrog")
        def sr2 = sa2.createDynamicUserList(30, "test2", 300, "jfrog")
        super.runReplication()
        def n1count = ArtUsers.getUserCount(node1HA)

        then:
        n1count == ArtUsers.getUserCount(node2Pro)

        when:
        Control.resume('8088')
        up = true
        sleep(SLEEP_TIME)
        super.runReplication()

        then:
        ArtUsers.getUserCount(node1HA) == ArtUsers.getUserCount(masterHA)

        cleanup:
        if (!up) Control.resume('8088')
    }

    // def "Set Master key encryption on Master node"() {
    //     when:
    //     def sa = new SecurityTestApi(masterHA)
    //     sa.deleteDefaultUser()
    //     def count = ArtUsers.getUserCount(masterHA)
    //     // TODO add support for setting encryption key
    //     // siMaster.setMasterEncryptKey()
    //     sa.createDefaultUserList()
    //     def saCount = sa.uList.userList.size()
    //     sleep(PTIMER)

    //     then:
    //     ArtUsers.getUserCount(masterHA) == count + saCount
    //     ArtUsers.getUserCount(node1HA) == count + saCount
    //     ArtUsers.getUserCount(node2Pro) == count + saCount
    // }

    // For now make sure rerun the whole test suite.
    // TODO: Make the smoke test agnostic to the system configuration i.e. Master Key encryption.
    // def "Set Master key encryption on all nodes"() {
    //     siMaster.setMasterEncryptKey()
    //     siNode1.setMasterEncryptKey()
    //     siNode2.setMasterEncryptKey()
    // }
}
