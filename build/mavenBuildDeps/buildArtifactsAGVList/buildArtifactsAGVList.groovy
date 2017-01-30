import org.artifactory.build.*
import org.artifactory.exception.CancelException
import org.jfrog.build.api.release.Promotion
import groovy.json.JsonSlurper
import groovy.json.JsonBuilder
import org.artifactory.resource.ResourceStreamHandle
import org.artifactory.api.build.BuildService
import java.io.File
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
     * http://localhost:8080/artifactory/api/plugins/execute/MavenDep?params=Generic=Generic|buildNumber=67
     *      promoteWithDeps - plugin name
     *      Generic - build to promote
     *      67 - build number
     */



    MavenDep(httpMethod: 'POST', users: ["jenkins"], groups: [], params: [buildName: '', buildNumber: '', buildStartTime: '']) { params ->

        buildName = getStringProperty(params, 'buildName', true)
        buildNumber = getStringProperty(params, 'buildNumber', true)
        buildStartTime = getStringProperty(params, 'buildStartTime', false)

        def artifactList = new ArrayList()
        def artifactListAGV = new ArrayList()
        List<BuildRun> buildsRun = builds.getBuilds(buildName, buildNumber, buildStartTime)
        if (buildsRun.size() > 1) log.info('Found two matching build to promote, please provide build start time', null, 409)

        def buildRun = buildsRun[0]
        if (buildRun == null) log.info("Build $buildName/$buildNumber was not found", null, 409)
        DetailedBuildRun stageBuild = builds.getDetailedBuild(buildRun)

        List<Array> buildArtifacts = stageBuild.build.modules

        if (buildArtifacts != null) {
            for (int j = 0; j < buildArtifacts.size(); j++) {
                artifacts = buildArtifacts.get(j).artifacts
                artifactList.add(artifacts[0])
            }
        }

        List<Array> buildDependencies = stageBuild.build.buildDependencies

        buildService = ctx.beanForType(BuildService.class)


        if (buildDependencies != null) {
            for (int i = 0; i < buildDependencies.size(); i++) {
                depBuildName = buildDependencies.get(i).name
                depBuildNumber = buildDependencies.get(i).number
                depBuildStartTime = buildDependencies.get(i).started
                List<BuildRun> depBuildRun = builds.getBuilds(depBuildName, depBuildNumber, depBuildStartTime)
                if (depBuildRun.size() > 1) log.info('Found two matching build to promote, please provide build start time', null, 409)
                if (depBuildRun == null || depBuildRun.size() == 0) log.info("Build $depBuildName/$depBuildNumber was not found", null, 409)
                DetailedBuildRun depStageBuild = builds.getDetailedBuild(depBuildRun)
                List<Array> depbuildArtifacts = depStageBuild.build.modules

                if (depbuildArtifacts != null) {
                    for (int j = 0; j < depbuildArtifacts.size(); j++) {
                        artifacts = depbuildArtifacts.get(j).artifacts
                        artifactList.add(artifacts[0])
                    }
                }
            }
        }


        if (artifactList != null) {
            for (int k = 1; k < artifactList.size(); k++) {
                sha1 = artifactList.get(k).sha1
                def paths = searches.artifactsBySha1(sha1)
                def path = paths[0]
                def layoutinfo = repositories.getLayoutInfo(path)
                def groupId = layoutinfo.getOrganization()
                def version = layoutinfo.getBaseRevision()
                def artifactId = layoutinfo.getModule()
                def fileversion = layoutinfo.getFileIntegrationRevision()
                if (fileversion != null && fileversion.length() > 0) {
                    version += "-" + fileversion
                }
                def AGV = ["groupId": groupId, "artifactId": artifactId, "version": version]
                artifactListAGV.addAll(AGV)
            }
        }

        def builder = new groovy.json.JsonBuilder(artifactListAGV)
        message = "$builder"
        log.info message
        status = 200
    }
}

private String getStringProperty(params, pName, mandatory) {
    def key = params[pName]
    def val = key == null ? null : key[0].toString()
    if (mandatory && val == null) log.info("$pName is mandatory paramater", null, 400)
    return val
}


