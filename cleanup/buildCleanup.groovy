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
import org.artifactory.repo.RepoPath

//curl -f -XPOST -u admin:password http://repo-demo:9090/artifactory/api/plugins/execute/clean-builds?params=days=50

def config = new ConfigSlurper().parse(new File("${System.properties.'artifactory.home'}/etc/plugins/buildCleanup.properties").toURI().toURL())

executions {
    cleanBuilds() { params ->
        def days = params['days'] ? params['days'][0] as int : 2
        def dryRun = params['dryRun'] ? params['dryRun'][0] as boolean : true
        buildCleanup(days, dryRun)
    }
}

jobs {
    buildCleanup(cron: "0/10 * * * * ?") {
        buildCleanup(config.days, config.dryRun);
    }
}

private def buildCleanup(int days, dryRun) {
    echo "Starting build cleanup older than $days days..."

    /*
    WARNING: UNSUPPORTED INTERNAL API USAGE!
     */
    //TODO: Remove this and replace with the following line once the new build.buildNames API is available
    /*List<String> buildNames = ctx.beanForType(
            org.artifactory.storage.build.service.BuildStoreService).getAllBuildNames()*/

    // Below is available only from Artifactory 3.2.0, see: https://www.jfrog.com/jira/browse/RTFACT-5942
    List<String> buildNames = builds.buildNames

    def n = 0
    buildNames.each { buildName ->
        builds.getBuilds(buildName, null, null).each {
            def before = new Date() - days
            //log.warn "Found build $buildName#$it.number: $it.startedDate"
            if (it.startedDate.before(before)) {
                echo "Deleting build: $buildName#$it.number ($it.startedDate)"
                if (!dryRun) {
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