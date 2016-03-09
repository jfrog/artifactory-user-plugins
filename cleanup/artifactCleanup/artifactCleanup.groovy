/*
 * Copyright (C) 2014 JFrog Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

class Global {
    static Boolean stopCleaning = false;
    static Boolean pauseCleaning = false;
    static int paceTimeMS = 0;
}

// curl command example for running this plugin.
// curl -i -uadmin:password -X POST "http://localhost:8081/artifactory/api/plugins/execute/cleanup?params=months=1|repos=libs-release-local|dryRun=true|paceTimeMS=2000"
//
// For a HA cluster, the following commands have to be directed at the instance running the script. Therefore it is best to invoke
// the script directly on an instance so the below commands can operate on same instance
// curl -i -uadmin:password -X POST "http://localhost:8081/artifactory/api/plugins/execute/cleanupCtl?params=command=pause"
// curl -i -uadmin:password -X POST "http://localhost:8081/artifactory/api/plugins/execute/cleanupCtl?params=command=resume"
// curl -i -uadmin:password -X POST "http://localhost:8081/artifactory/api/plugins/execute/cleanupCtl?params=command=stop"
// curl -i -uadmin:password -X POST "http://localhost:8081/artifactory/api/plugins/execute/cleanupCtl?params=command=adjustPaceTimeMS|value=-1000"

executions {
    cleanup() { params ->
        def months = params['months'] ? params['months'][0] as int : 6
        def repos = params['repos'] as String[]
        def dryRun = params['dryRun'] ? params['dryRun'][0] as boolean : false
        Global.paceTimeMS = params['paceTimeMS'] ? params['paceTimeMS'][0] as int : 0
        artifactCleanup(months, repos, log, dryRun)
    }
    
    cleanupCtl() { params ->
        def command = params['command'] ? params['command'][0] as String : ''
  
        switch ( command ) {
        case "stop":
            Global.stopCleaning = true
            log.info "Stop request detected"
            break;
        case "adjustPaceTimeMS":
            def adjustPaceTimeMS = params['value'] ? params['value'][0] as int : 0
            Global.paceTimeMS += adjustPaceTimeMS
            log.info "Pacing adjustment request detected, adjusting pace time by $adjustPaceTimeMS to new value of $Global.paceTimeMS"
            break;
        case "pause":
            Global.pauseCleaning = true
            log.info "Pause request detected"
            break;
        case "resume":
            Global.pauseCleaning = false
            log.info "Resume request detected"
            break;
        default:
            log.info "Missing or invalid command, '$command'"
        }
    }
}

jobs {
    scheduledCleanup(cron: "0 0 5 ? * 1") {
        def config = new ConfigSlurper().parse(new File(ctx.artifactoryHome.haAwareEtcDir, "plugins/artifactCleanup.properties").toURL())
        log.info "Schedule job policy list: $config.policies"

        config.policies.each{ policySettings ->
            def repos = policySettings[ 0 ] ? policySettings[ 0 ] as String[] : ["__none__"]
            def months = policySettings[ 1 ] ? policySettings[ 1 ] as int : 6
            Global.paceTimeMS = policySettings[ 2 ] ? policySettings[ 2 ] as int : 0
            def dryRun = policySettings[ 3 ] ? policySettings[ 3 ] as Boolean : false
            
            log.info "Policy settings for scheduled run: repo list($repos), months($months), paceTimeMS($Global.paceTimeMS) dryrun($dryRun)"
            artifactCleanup( months, repos, log, dryRun )
        }
    }
}

private def artifactCleanup(int months, String[] repos, log, dryRun = false) {
    log.info "Starting artifact cleanup for repositories $repos, until $months months ago with pacing interval $Global.paceTimeMS ms dryrun: $dryRun"

    def monthsUntil = Calendar.getInstance()
    monthsUntil.add(Calendar.MONTH, -months)

    Global.stopCleaning = false
    int foundArtifacts = 0
    long bytesFound = 0
    def artifactsCleanedUp = searches.artifactsNotDownloadedSince(monthsUntil, monthsUntil, repos)
    artifactsCleanedUp.
        find {
            while ( Global.pauseCleaning ) {
                log.info "Pausing by request"
                sleep( 60000 )
            }

            if ( Global.stopCleaning ) {
                log.info "Stopping by request, ending loop"
                return true
            }
            
            bytesFound += repositories.getItemInfo(it)?.getSize()
            foundArtifacts++
            if (dryRun) {
                log.info "Found $it, $foundArtifacts/$artifactsCleanedUp.size total $bytesFound bytes"
            } else {
                log.info "Deleting $it, $foundArtifacts/$artifactsCleanedUp.size total $bytesFound bytes"
                repositories.delete it
            }
            
            if (Global.paceTimeMS > 0) {
                sleep( Global.paceTimeMS )
            }

            return false
        }

    if (dryRun) {
        log.info "Dry run - nothing deleted. found $foundArtifacts artifacts consuming $bytesFound bytes"
    } else {
        log.info "Finished cleanup, deleted $foundArtifacts artifacts that took up $bytesFound bytes"
    }
}
