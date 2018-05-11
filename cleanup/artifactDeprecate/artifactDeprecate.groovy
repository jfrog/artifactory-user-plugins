import org.apache.commons.lang3.StringUtils
import org.artifactory.api.repo.exception.ItemNotFoundRuntimeException

import groovy.time.TimeCategory
import groovy.time.TimeDuration
import groovy.transform.Field
import org.artifactory.security.UserInfo

// ToDO:
// 1. property file with scheduler config
// 2. fix numberArtifactsToKeep to keep provided number of artifacts, not deleting as much artifacts instead

def pluginGroup = 'deprecates'

executions {
    deprecate(groups: [pluginGroup]) { params ->
        def months = params['months'] ? params['months'][0] as int : 6
        def repos = params['repos'] as String[]
        def dryRun = params['dryRun'] ? params['dryRun'][0].toBoolean() : false      
        def numberArtifactsToKeep = params['numberArtifactsToKeep'] ? params['numberArtifactsToKeep'][0] as int : 3
        artifactDeprecate(months, repos, log, dryRun, numberArtifactsToKeep)
    }
}


private def artifactDeprecate(int months, String[] repos, log, dryRun = false, numberArtifactsToKeep = 3) {

	log.info "-----------[ Starting Deprecating Artifacts... ]-----------"
	log.info "---> Variables: \n months: $months \n repos: $repos \n log: $log \n dryRun: $dryRun \n numberArtifactsToKeep: $numberArtifactsToKeep \n"

	def monthsUntil = Calendar.getInstance()
    monthsUntil.add(Calendar.MONTH, -months)

    int cntFoundArtifacts = 0
    int cntNoDeletePermissions = 0
    def artifactsCleanedUp = searches.artifactsCreatedOrModifiedInRange(null, monthsUntil, repos)
    def sortedArtifactsCleanedUp = artifactsCleanedUp.sort { repositories.getItemInfo(it)?.lastUpdated }
    // Here we can use sortedArtifactsCleanedUp.length - numberArtifactsToKeep 
    def artifactsToDelete = sortedArtifactsCleanedUp.take(numberArtifactsToKeep)


    log.info "\n ===> sortedArtifactsCleanedUp: $sortedArtifactsCleanedUp\n\n ===> artifactsToDelete: $artifactsToDelete"

    artifactsToDelete.find {
        try {
            if (!security.canDelete(it)) {
                cntNoDeletePermissions++
            }
            if (dryRun) {
                    log.info "Found $it!"
                    log.info "\t==> currentUser: ${security.currentUser().getUsername()}"
                    log.info "\t==> canDelete: ${security.canDelete(it)}"
            } else {
                if (security.canDelete(it)) {
                    log.info "Deleting $it!"
                    repositories.delete it
                } else {
                    log.info "Can't delete $it (user ${security.currentUser().getUsername()} has no delete permissions), " +
                            "$cntFoundArtifacts/$artifactsCleanedUp.size"
                    cntNoDeletePermissions++
                }
            }
        } catch (ItemNotFoundRuntimeException ex) {
            log.info "Failed to find $it, skipping"
        }

        return false
    }
}


