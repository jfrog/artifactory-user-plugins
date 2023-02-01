package SecurityReplicationTest

import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(Suite.class)
@Suite.SuiteClasses([
        artifactory.RepositoryTestApiTest.class,
        artifactory.SecurityTestApiTest.class,
        SecurityReplicationTest.class,
        UserReplication.class,
        GroupReplication.class,
        PermissionReplication.class,
        Shutdown.class
])
class TestSuite {
}
