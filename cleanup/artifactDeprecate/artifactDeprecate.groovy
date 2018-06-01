import org.apache.commons.lang3.StringUtils
import org.artifactory.api.repo.exception.ItemNotFoundRuntimeException

import groovy.time.TimeCategory
import groovy.time.TimeDuration
import groovy.transform.Field
import org.artifactory.security.UserInfo

// Usage example:
// curl -i -uadmin:password -X POST "http://localhost:8081/artifactory/api/plugins/execute/deprecate?params=months=0;repos=docker-local;directory=android;dryRun=true;numberArtifactsToKeep=3"
// curl -i -uadmin:password -X POST "http://localhost:8081/artifactory/api/plugins/execute/deprecate?params=months=3;repos=docker-local;directory=ansible;numberArtifactsToKeep=3"

@Field final String PROPERTIES_FILE_PATH = "plugins/${this.class.name}.properties"
def config = new ConfigSlurper().parse(new File(ctx.artifactoryHome.haAwareEtcDir, PROPERTIES_FILE_PATH).toURL())

config.policies.each{ policySettings ->
    def cron = policySettings[ 0 ] ? policySettings[ 0 ] as String : ["0 0 5 ? * 1"]
    def repos = policySettings[ 1 ] ? policySettings[ 1 ] as String[] : ["__none__"]
    def directory = policySettings[ 2 ] ? policySettings[ 2 ] as String : ["__none__"]
    def months = policySettings[ 3 ] ? policySettings[ 3 ] as int : 6
    def dryRun = policySettings[ 4 ] ? policySettings[ 4 ] as Boolean : false
    def numberArtifactsToKeep = policySettings[ 5 ] ? policySettings[ 5 ] as int : 3

    log.info "Schedule job policy list: $config.policies"

    jobs {
        "scheduledCleanup_$cron"(cron: cron) {
            log.info "Policy settings for scheduled run at($cron): repo list($repos), directory($directory), months($months), dryrun($dryRun), numberArtifactsToKeep($numberArtifactsToKeep)"
            artifactDeprecate( months, repos, directory, log, dryRun, numberArtifactsToKeep )
        }
    }
}

def pluginGroup = 'deprecates'

executions {
    deprecate(groups: [pluginGroup]) { params ->
        def months = params['months'] ? params['months'][0] as int : 6
        def repos = params['repos'] as String[]
        def directory = params['directory'][0] as String
        def dryRun = params['dryRun'] ? params['dryRun'][0].toBoolean() : false      
        def numberArtifactsToKeep = params['numberArtifactsToKeep'] ? params['numberArtifactsToKeep'][0] as int : 3
        artifactDeprecate(months, repos, directory, log, dryRun, numberArtifactsToKeep)
    }
}


private def artifactDeprecate(int months, String[] repos, String directory, log, dryRun = false, numberArtifactsToKeep = 3) {

	log.info "-----------[ Starting Deprecating Artifacts... ]-----------"
	log.info "---> Variables: \n months: $months \n repos: $repos \n directory: ${directory} \n log: $log \n dryRun: $dryRun \n numberArtifactsToKeep: $numberArtifactsToKeep \n"

	def monthsUntil = Calendar.getInstance()
    monthsUntil.add(Calendar.MONTH, -months)

    int cntFoundArtifacts = 0
    int cntNoDeletePermissions = 0
    def artifactsList = searches.artifactsCreatedOrModifiedInRange(null, monthsUntil, repos)
    def artifactsCleanedUp = artifactsList.findAll { it =~ directory }
    def sortedArtifactsCleanedUp = artifactsCleanedUp.sort { repositories.getItemInfo(it)?.lastUpdated }
    int numberArtifactsToDelete = sortedArtifactsCleanedUp.size - numberArtifactsToKeep 
    def artifactsToDelete = sortedArtifactsCleanedUp.take(numberArtifactsToDelete)


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