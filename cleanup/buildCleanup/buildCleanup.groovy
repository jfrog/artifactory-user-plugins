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

// curl -X POST -v -u admin:password "http://localhost:8080/artifactory/api/plugins/execute/cleanBuilds?params=days=50|dryRun"

executions {
    cleanBuilds() { params ->
        def days = params['days'] ? params['days'][0] as int : 2
        def dryRun = params['dryRun'] ? params['dryRun'][0] as boolean : false
        buildCleanup(days, dryRun)
    }
}

jobs {
    buildCleanup(cron: "0 0 12 1/1 * ? *") {
        def config = new ConfigSlurper().parse(new File("${System.properties.'artifactory.home'}/etc/plugins/buildCleanup.properties").toURI().toURL())
        buildCleanup(config.days, config.dryRun)
    }
}

private def buildCleanup(int days, dryRun) {
    if (dryRun) {
        echo "**DryRun** Starting build cleanup older than $days days..."
    } else {
        echo "Starting build cleanup older than $days days..."
    }
    List<String> buildNames = builds.buildNames

    def n = 0
    buildNames.each { buildName ->
        builds.getBuilds(buildName, null, null).each {
            def before = new Date() - days
            // log.warn "Found build $buildName#$it.number: $it.startedDate"
            if (it.startedDate.before(before)) {
                if (dryRun) {
                    log.info "Found $it"
                    echo "**DryRun** Deleting build: $buildName#$it.number ($it.startedDate)"
                } else {
                    echo "Deleting build: $buildName#$it.number ($it.startedDate)"
                    builds.deleteBuild it
                }
                n++
            }
        }
    }
    if (dryRun) {
        echo "**DryRun** Finished build cleanup older than $days days. $n builds were deleted."
    } else {
        echo "Finished build cleanup older than $days days. $n builds were deleted."
    }
}

private void echo(msg) {
    log.warn msg
}
