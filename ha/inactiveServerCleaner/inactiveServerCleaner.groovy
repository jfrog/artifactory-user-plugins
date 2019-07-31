import groovy.transform.Field
import org.artifactory.common.ConstantValues
import org.artifactory.state.ArtifactoryServerState
import org.artifactory.storage.db.servers.model.ArtifactoryServer
import org.artifactory.storage.db.servers.service.ArtifactoryServersCommonService
import org.slf4j.Logger

import java.util.concurrent.TimeUnit

@Field
ArtifactoryServersCommonService artifactoryServersCommonService = ctx.beanForType(ArtifactoryServersCommonService)

@Field
ArtifactoryInactiveServersCleaner artifactoryInactiveServerCleaner = new ArtifactoryInactiveServersCleaner(artifactoryServersCommonService, log)

jobs {
    clean(interval: 90000, delay: 900000) {
        runCleanupHAInactiveServers()
    }
}

executions {
    cleanHAInactiveServers() { params ->
        runCleanupHAInactiveServers()
    }
}

void runCleanupHAInactiveServers() {
    artifactoryInactiveServerCleaner.cleanInactiveArtifactoryServers()
}

class ArtifactoryInactiveServersCleaner {

    private ArtifactoryServersCommonService artifactoryServersCommonService
    private Logger log

    ArtifactoryInactiveServersCleaner(ArtifactoryServersCommonService artifactoryServersCommonService, Logger log) {
        this.artifactoryServersCommonService = artifactoryServersCommonService
        this.log = log
    }

    def cleanInactiveArtifactoryServers() {
        log.info "Executing inactive artifactory servers cleaner plugin"
        List<ArtifactoryServer> allMembers = artifactoryServersCommonService.getAllArtifactoryServers()
        for (member in allMembers) {
            long heartbeat = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - member.getLastHeartbeat())
            boolean noHeartbeat = heartbeat > ConstantValues.haHeartbeatStaleIntervalSecs.getInt()
            if (member.getServerState() == ArtifactoryServerState.UNAVAILABLE || (noHeartbeat && member.getServerState() != ArtifactoryServerState.CONVERTING && member.getServerState() != ArtifactoryServerState.STARTING)) {
                try {
                    log.info "Inactive artifactory servers cleaning task found server ${member.serverId} to remove"
                    artifactoryServersCommonService.removeServer(member.serverId)
                } catch (Exception e) {
                    log.error "Error: Not able to remove ${member.serverId}, ${e.message}"
                }
            }
        }
        log.info "No inactive servers found"
    }
}
