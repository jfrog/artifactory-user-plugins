import org.artifactory.repo.RepoPath

//curl -f -XPOST -u admin:password http://repo-demo:9090/artifactory/api/plugins/execute/clean-builds?params=days=50

executions {
    "clean-builds"() { params ->
        def days = params['days'] ? params['days'][0] as int : 2
        def dryRun = params['dryRun'] ? params['dryRun'][0] as boolean : true
        buildCleanup(days, dryRun)
    }
}

jobs {
    buildCleanup(cron: "0 0 5 ? * 1") {
        def config = new ConfigSlurper().parse(new File("${System.properties.'artifactory.home'}/etc/plugins/buildCleanup.properties").toURI().toURL())
        buildCleanup(config.days, config.dryRun);
    }
}

private def buildCleanup(int days, dryRun) {
    echo "Starting build cleanup older than $days days..."

    /*
    WARNING: UNSUPPORTED INTERNAL API USAGE!
     */
    //TODO: Remove this and replace with the following line once the new build.buildNames API is available
    List<String> buildNames = ctx.beanForType(
            org.artifactory.storage.build.service.BuildStoreService).getAllBuildNames()
    //List<String> buildNames = builds.buildNames

    def n = 0
    buildNames.each { buildName ->
        builds.getBuilds(buildName, null, null).each {
            def before = new Date() - days
            //log.warn "Found build $buildName#$it.number: $it.startedDate"
            if (it.startedDate.before(before)) {
                if (dryRun) {
                    echo "Deleting build: $buildName#$it.number ($it.startedDate)"
                } else {
                    //builds.deleteBuild it
                }
                n++;
            }
        }
    }

    echo "Finished build cleanup older than $days days. $n builds were deleted."
}

private void echo(msg) {
    log.warn msg
}