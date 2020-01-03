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


import groovy.json.JsonBuilder
import groovy.transform.Field
import org.artifactory.api.common.BasicStatusHolder
import org.artifactory.api.build.BuildService
import org.artifactory.exception.CancelException

// Example REST API calls:
// curl -X POST -v -u admin:password "http://localhost:8080/artifactory/api/plugins/execute/cleanOldBuilds?params=buildName=test|buildNumber=1|cleanArtifacts=true"
// curl -X POST -v -u admin:password "http://localhost:8080/artifactory/api/plugins/execute/cleanOldBuilds?params=buildName=test;buildNumber=1;cleanArtifacts=true"

@Field final String BUILDS_FILE_PATH = "plugins/build.json"


executions {
    cleanOldBuilds() { params ->
        buildName = getStringProperty(params, 'buildName', true)
        buildNumber = getIntegerProperty(params, 'buildNumber', true)
        cleanArtifacts = getBooleanProperty(params, 'cleanArtifacts', false)
        buildCleanup(buildName, buildNumber, cleanArtifacts)
        message = "Build older then $buildName/$buildNumber has been successfully deleted"
        log.info message
        status = 200
    }
}

private def buildCleanup(buildName, buildNumber, cleanArtifacts) {
    def buildsRun
    log.info "Build Number is $buildNumber..."
    log.info "Looking for build with name $buildName..."
    buildsRun = builds.getBuilds(buildName, null, null)
    def buildList = []
    buildService = ctx.beanForType(BuildService.class)
    log.info "Sorting all $buildName builds with build number..."
    buildsRun.each { buildRun ->
        stageBuild = builds.getDetailedBuild(buildRun)
        if((stageBuild.build.number as Integer) <= buildNumber){
            buildList.add(stageBuild.build)
            if(cleanArtifacts){
                log.warn "Deleting build: $buildName#$stageBuild.build.number including build's artifacts.."
                buildService.deleteBuild(buildRun, cleanArtifacts as boolean, new BasicStatusHolder())
            } else {
                log.warn "Deleting build: $buildName#$stageBuild.build.number.."
                builds.deleteBuild buildRun
            }
        }
    }

    buildList.sort{it.number as Integer}
    log.info "Storing sorted build info in build.json file"
    new File(ctx.artifactoryHome.etcDir, BUILDS_FILE_PATH).write(new JsonBuilder(buildList).toPrettyString())
}

private String getStringProperty(params, pName, mandatory) {
    def key = params[pName]
    def val = key == null ? null : key[0].toString()
    if (mandatory && val == null) cancelCleanup("$pName is mandatory paramater", null, 400)
    return val
}

private Integer getIntegerProperty(params, pName, mandatory) {
    def key = params[pName]
    def val = key == null ? null : getInteger(key[0])
    if (mandatory && val == null) cancelCleanup("$pName is mandatory paramater", null, 400)
    return val
}

private Boolean getBooleanProperty(params, pName, mandatory) {
    def key = params[pName]
    def val = key == null ? null : key[0] as boolean
    if (mandatory && val == null) cancelCleanup("$pName is mandatory paramater", null, 400)
    return val
}

private Integer getInteger(numberStr) {
    val = numberStr.isInteger() ? numberStr.toInteger() : cancelCleanup("can not cast $numberStr to integer", null, 400)
    return val
}

def cancelCleanup(String message, Throwable cause, int errorLevel) {
    log.warn message
    throw new CancelException(message, cause, errorLevel)
}
