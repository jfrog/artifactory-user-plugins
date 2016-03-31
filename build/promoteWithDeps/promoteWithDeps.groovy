import org.artifactory.build.*
import org.artifactory.exception.CancelException
import org.jfrog.build.api.release.Promotion
import groovy.json.JsonSlurper
import org.artifactory.resource.ResourceStreamHandle
import org.artifactory.api.build.BuildService
import org.artifactory.common.StatusHolder


import java.lang.reflect.Array

import static groovy.xml.XmlUtil.serialize
import static org.artifactory.repo.RepoPathFactory.create

executions {

    /**
     * An promoteWithDeps definition.
     * The first value is a unique name for the promoteWithDeps.
     *
     * Context variables:
     * status (int) - a response status code. Defaults to -1 (unset). Not applicable for an async promoteWithDeps.
     * message (java.lang.String) - a text message to return in the response body, replacing the response content.
     *                              Defaults to null. Not applicable for an async promoteWithDeps.
     *
     * Plugin info annotation parameters:
     *  version (java.lang.String) - Closure version. Optional.
     *  description (java.lang.String) - Closure description. Optional.
     *  httpMethod (java.lang.String, values are GET|PUT|DELETE|POST) - HTTP method this closure is going
     *    to be invoked with. Optional (defaults to POST).
     *  params (java.util.Map<java.lang.String, java.lang.String>) - Closure default parameters. Optional.
     *  users (java.util.Set<java.lang.String>) - Users permitted to query this plugin for information or invoke it.
     *  groups (java.util.Set<java.lang.String>) - Groups permitted to query this plugin for information or invoke it.
     *
     * Closure parameters:
     *  params (java.util.Map) - An promoteWithDeps takes a read-only key-value map that corresponds to the REST request
     *    parameter 'params'. Each entry in the map contains an array of values. This is the default closure parameter,
     *    and so if not named it will be "it" in groovy.
     *  ResourceStreamHandle body - Enables you to access the full input stream of the request body.
     *    This will be considered only if the type ResourceStreamHandle is declared in the closure.

     REST call example (should be executed by admin or by user mentioned in
     * closure parameters):
     * http://localhost:8080/artifactory/api/plugins/execute/promoteWithDeps?params=Generic=Generic|buildNumber=67
     *      promoteWithDeps - plugin name
     *      Generic - build to promote
     *      67 - build number
     *      params - as explained above
     * Example RequestBody
     *{*        "status": "staged" // new build status (any string)
     *       "comment" : "Tested on all target platforms." // An optional comment describing the reason for promotion. Default: ""
     *       "ciUser": "builder" // The user that invoked promotion from the CI server
     *        "timestamp" : ISO8601 // the time the promotion command was received by Artifactory (It needs to be unique).
     *        "dryRun" : false // run without executing any operation in Artifactory, but get the results to check if the operation can succeed. Default: false
     *        "targetRepo" : "libs-release-local" // optional repository to move or copy the build's artifacts and/or dependencies
     *        "copy": false // whether to copy instead of move, when a target repository is specified. Default: false
     *        "artifacts" : true // whether to move/copy the build's artifacts. Default: true
     *        "dependencies" : true // whether to move/copy the build's dependencies. Default: false.
     *        "scopes" : [ "compile", "runtime" ] // an array of dependency scopes to include when "dependencies" is true
     *        "properties": { // a list of properties to attach to the build's artifacts (regardless if "targetRepo" is used).
     *        "components": ["c1","c3","c14"],
     *        "release-name": ["fb3-ga"]
     *}*        "failFast": true // fail and abort the operation upon receiving an error. Default: true
     *}*/



    promoteWithDeps(httpMethod: 'POST', users: ["jenkins"], groups: [], params: [buildName: '', buildNumber: '', buildStartTime: '']) { params, ResourceStreamHandle body ->

        buildName = getStringProperty(params, 'buildName', true)
        buildNumber = getStringProperty(params, 'buildNumber', true)
        buildStartTime = getStringProperty(params, 'buildStartTime', false)

        bodyJson = new JsonSlurper().parse(body.inputStream)


        List<BuildRun> buildsRun = builds.getBuilds(buildName, buildNumber, buildStartTime)
        if (buildsRun.size() > 1) cancelPromotion('Found two matching build to promote, please provide build start time', null, 409)

        def buildRun = buildsRun[0]
        if (buildRun == null) cancelPromotion("Build $buildName/$buildNumber was not found, canceling promotion", null, 409)
        DetailedBuildRun stageBuild = builds.getDetailedBuild(buildRun)
        List<Array> buildDependencies = stageBuild.build.buildDependencies

        buildService = ctx.beanForType(BuildService.class)

        if (buildDependencies != null) {
            for (int i = 0; i < buildDependencies.size(); i++) {
                depBuildName = buildDependencies.get(i).name
                depBuildNumber = buildDependencies.get(i).number
                depBuildStartTime = buildDependencies.get(i).started
                def jsonSlurper = new JsonSlurper()
                def props = jsonSlurper.parseText("{ \"properties\":{\"parent.buildName\":[\"${buildName}\"],\"parent.buildNumber\":[\"${buildNumber}\"]}}");
                bodyJson.putAll(props);

                promotion = new Promotion(bodyJson.status, bodyJson.comment, bodyJson.ciUser, bodyJson.timestamp, bodyJson.dryRun ?: false,
                        getTargetRepo(bodyJson.targetRepo, depBuildName), bodyJson.sourceRepo, bodyJson.copy ?: false, bodyJson.artifacts == null ? true : bodyJson.artifacts, bodyJson.dependencies ?: false, bodyJson.scopes as Set<String>, bodyJson.properties, bodyJson.failFast == null ? true : bodyJson.failFast)

                List<BuildRun> depBuildRun = builds.getBuilds(depBuildName, depBuildNumber, depBuildStartTime)
                if (depBuildRun.size() > 1) cancelPromotion('Found two matching build to promote, please provide build start time', null, 409)
                if (depBuildRun == null || depBuildRun.size() == 0) cancelPromotion("Build $depBuildName/$depBuildNumber was not found, canceling promotion", null, 409)
                DetailedBuildRun depStageBuild = builds.getDetailedBuild(depBuildRun)
                try {
                    buildService.promoteBuild(depStageBuild, promotion)
                } catch (Exception e) {
                    log.info "error is promotiong build $depBuildName/$depBuildNumber"
                    cancelPromotion("Rolling back build promotion", null, 500)

                }
            }
        }


        promotion = new Promotion(bodyJson.status, bodyJson.comment, bodyJson.ciUser, bodyJson.timestamp, bodyJson.dryRun ?: false,
                getTargetRepo(bodyJson.targetRepo, buildName), bodyJson.sourceRepo, bodyJson.copy ?: false, bodyJson.artifacts == null ? true : bodyJson.artifacts, bodyJson.dependencies ?: false, bodyJson.scopes as Set<String>, bodyJson.properties as Map<String, Collection<String>>, bodyJson.failFast == null ? true : bodyJson.failFast)

        try {
            buildService.promoteBuild(stageBuild, promotion)
        } catch (Exception e) {
            info.log "error is promotiong build $depBuildName/$depBuildNumber"
            cancelPromotion('Rolling back build promotion', cause, statusCode)
        }
        message = " Build  $buildName/$buildNumber has been successfully promoted"
        log.info message
        status = 200
    }
}

private String getStringProperty(params, pName, mandatory) {
    def key = params[pName]
    def val = key == null ? null : key[0].toString()
    if (mandatory && val == null) cancelPromotion("$pName is mandatory paramater", null, 400)
    return val
}

def cancelPromotion(String message, Throwable cause, int errorLevel) {
    log.warn message
    throw new CancelException(message, cause, errorLevel)
}

private String getTargetRepo(def targetRepo, String depBuildName) {
    if (targetRepo instanceof String || targetRepo == null) {
        return targetRepo
    } else {
        def depTargetRepo = targetRepo[depBuildName]
	log.warn "Found target repo $depTargetRepo for build $depBuildName"
	return depTargetRepo 
    }
}
