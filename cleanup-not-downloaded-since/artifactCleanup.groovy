executions {
    cleanup() { params ->
        def months = params['months'] ? params['months'][0] as int: 6
        def repos = params['repos'] as String[]
        artifactCleanup(months, repos, log)
    }
}

jobs {
    scheduledCleanup(cron: "0 0 5 ? * 1") {
        def config = new ConfigSlurper().parse(new File("${System.properties.'artifactory.home'}/etc/plugins/artifactCleanup.properties").toURL())
        artifactCleanup(config.monthsUntil, config.repos as String[], log);
    }
}

private def artifactCleanup(months, repos, log) {
    log.info "Starting artifact cleanup for repositories $repos, until $months months ago"

    def monthsUntil = Calendar.getInstance()
    monthsUntil.add(Calendar.MONTH, -months)
    def toInMillis = monthsUntil.getTimeInMillis()

    def artifactsCleanedUp = searches.artifactsCreatedOrModifiedInRange(null, monthsUntil, repos).findAll {
        def xml = repositories.getXmlMetadata(it, 'artifactory.stats')
        xml == null || Long.valueOf(new XmlSlurper().parseText(xml).lastDownloaded.text()) < toInMillis
    }.each {
        log.info "Deleting $it"
        repositories.undeploy it
    }
    log.info "Finished cleanup, deleted $artifactsCleanedUp.size artifacts"
}
