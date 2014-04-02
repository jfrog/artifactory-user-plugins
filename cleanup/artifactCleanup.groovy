//curl command example for running this plugin. 
//curl -i -uadmin:password -X POST "http://localhost:8081/artifactory/api/plugins/execute/cleanup?params=months=1|repos=libs-release-local|log|dryRun"
executions {
    cleanup() { params ->
        def months = params['months'] ? params['months'][0] as int: 6
        def repos = params['repos'] as String[]
        def dryRun = params['dryRun'] ? params['dryRun'][0] as boolean: false
        artifactCleanup(months, repos, log, dryRun)
    }
}

jobs {
    scheduledCleanup(cron: "0 0 5 ? * 1") {
        def config = new ConfigSlurper().parse(new File("${System.properties.'artifactory.home'}/etc/plugins/artifactCleanup.properties").toURL())
        artifactCleanup(config.monthsUntil, config.repos as String[], log, false);
    }
}

private def artifactCleanup(int months, String[] repos, log, dryRun) {
    log.info "Starting artifact cleanup for repositories $repos, until $months months ago"

    def monthsUntil = Calendar.getInstance()
    monthsUntil.add(Calendar.MONTH, -months)

    def artifactsCleanedUp =
        searches.artifactsNotDownloadedSince(monthsUntil, monthsUntil, repos).
        each {
            if (dryRun) {
                log.info "Found $it";
            } else {
                log.info "Deleting $it";
                repositories.delete it
            }
        }

    if (dryRun) {
        log.info "Dry run - nothing deleted. found $artifactsCleanedUp.size artifacts"
    } else {
        log.info "Finished cleanup, deleted $artifactsCleanedUp.size artifacts"
    }
}
