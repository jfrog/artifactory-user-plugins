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

import groovy.transform.Field
import groovy.xml.StreamingMarkupBuilder
import org.artifactory.build.*
import org.artifactory.common.StatusHolder
import org.artifactory.exception.CancelException
import org.artifactory.fs.FileInfo
import org.artifactory.repo.RepoPath
import org.artifactory.util.StringInputStream

import static groovy.xml.XmlUtil.serialize
import static org.artifactory.repo.RepoPathFactory.create

promotions {
    /**
     * A REST executable build promotion.
     *
     * This plugin promotes a snapshot build to release. It does the following:
     *
     *  1. The build is copied with "-r" suffix added to build number to
     *     indicate release.
     *  2. The produced artifacts and the dependencies are copied to the target
     *     release repository and renamed from snapshot to release version (by
     *     using repository layout or the snapExp parameter).
     *  3. Descriptors (ivy.xml or pom.xml) are modified by replacing the
     *     versions from snapshot to release (including dependencies) and
     *     deployed to the target release repository.
     *
     * Plugin parameters (passed via REST call):
     *  * snapExp - snapshot version regular expression.
     *    It is used as fallback to determine how to transform a snapshot
     *    version string to a release one in case when repository layout
     *    information can't be used for this purpose (e.g. the layout doesn't
     *    match).
     *  * targetRepository - the name of repository to put the promoted build
     *    artifacts in.
     *
     * REST call example (should be executed by admin or by user mentioned in
     * closure parameters):
     * http://repo-demo:8080/artifactory/api/plugins/build/promote/snapshotToRelease/gradle-multi-example/1?params=snapExp=d%7B14%7D|targetRepository=gradle-release-local
     *      snapshotToRelease - plugin name
     *      gradle-multi-example - build to promote
     *      1 - build number
     *      params - as explained above
     */
    snapshotToRelease(users: "jenkins", params: [snapExp: 'd{14}', targetRepository: 'gradle-release-local']) { buildName, buildNumber, params ->
        log.info 'Promoting build: ' + buildName + '/' + buildNumber

        // 1. Extract properties
        buildStartTime = getStringProperty(params, 'buildStartTime', false)
        String snapExp = getStringProperty(params, 'snapExp', true)
        String targetRepository = getStringProperty(params, 'targetRepository', true)
        // 2. Get Stage build information by name/number
        // Sanity check
        List<BuildRun> buildsRun = builds.getBuilds(buildName, buildNumber, buildStartTime)
        if (buildsRun.size() > 1) cancelPromotion('Found two matching build to promote, please provide build start time', null, 409)

        def buildRun = buildsRun[0]
        if (buildRun == null) cancelPromotion("Build $buildName/$buildNumber was not found, canceling promotion", null, 409)
        DetailedBuildRun stageBuild = builds.getDetailedBuild(buildRun)
        String releasedBuildNumber = "$stageBuild.number-r"
        Set<FileInfo> stageArtifactsList = builds.getArtifactFiles(buildRun)

        if (!builds.getBuilds(buildName, "$stageBuild.number-r", null).empty) {
            cancelPromotion("Build $buildName/$buildNumber was already promoted under build number$releasedBuildNumber", null, 400)
        }

        // 3. Prepare release DetailedBuildRun and release artifacts for deployment
        @Field String timestamp = System.currentTimeMillis().toString()
        DetailedBuildRun releaseBuild = stageBuild.copy(releasedBuildNumber)
        releaseBuild.properties
        releaseArtifactsSet = [] as Set<RepoPath>
        List<Module> modules = releaseBuild.modules
        // Modify this condition to fit your needs
        if (!(snapExp == 'd14' || snapExp == 'SNAPSHOT')) cancelPromotion('This plugin logic support only Unique/Non-Unique snapshot patterns', null, 400)
        // If there is mor then one Artifacts that have the same checksum but different name only the first one will be return in the search so they will have to have different care
        def missingArtifacts = []
        // Iterate over modules list
        modules.each { Module module ->
            // Find project inner module dependencies
            List<FileInfo> innerModuleDependencies = []
            def dependenciesList = module.dependencies
            dependenciesList.each { dep ->
                FileInfo res = stageArtifactsList.asList().find { sal -> sal.checksumsInfo.sha1 == dep.sha1 }
                if (res != null) innerModuleDependencies << res
            }

            // Find and set module ID with release version
            def id = module.id
            def moduleInfo = parseModuleId(id, snapExp)
            module.id = moduleInfo.id

            // Iterate over the artifact list, create a release artifact deploy it and add it to the release DetailedBuildRun
            // Save a copy of the RepoPath to roll back if needed
            List<Artifact> artifactsList = module.artifacts
            RepoPath releaseRepoPath = null
            try {
                artifactsList.eachWithIndex { art, index ->
                    def stageRepoPath = getStageRepoPath(art, stageArtifactsList)
                    if (stageRepoPath != null) {
                        releaseRepoPath = getReleaseRepoPath(targetRepository, stageRepoPath, moduleInfo.stageVersion, snapExp)
                    } else {
                        missingArtifacts << art
                        return
                    }

                    // If ivy.xml or pom then create and deploy a new Artifact with the fix revision,status,publication inside the xml
                    StatusHolder status
                    switch (art.type) {
                        case 'ivy':
                            status = generateAndDeployReleaseIvyFile(stageRepoPath, releaseRepoPath, innerModuleDependencies, snapExp)
                            break
                        case 'pom':
                            status = generateAndDeployReleasePomFile(stageRepoPath, releaseRepoPath, innerModuleDependencies, stageArtifactsList, snapExp)
                            break
                        default:
                            status = repositories.copy(stageRepoPath, releaseRepoPath)
                    }
                    if (status.isError()) rollback(releaseBuild, releaseArtifactsSet, status.exception)
                    setReleaseProperties(stageRepoPath, releaseRepoPath)
                    releasedArtifact = new Artifact(repositories.getFileInfo(releaseRepoPath), art.type)
                    artifactsList[index] = releasedArtifact

                    // Add the release RepoPath for roll back
                    releaseArtifactsSet << releaseRepoPath
                }
            } catch (IllegalStateException e) {
                rollback(releaseBuild, releaseArtifactsSet, e.message, e)
            }
        }

        // Fix dependencies of other modules with release version
        try {
            modules.each { mod ->
                releaseDependencies = []
                def dependenciesList = mod.dependencies
                dependenciesList.each { dep ->
                    def match = stageArtifactsList.asList().find { item ->
                        item.checksumsInfo.sha1 == dep.sha1
                    }
                    if (match != null) {
                        // interproject dependency, change it
                        // Until GAP-129 is resolved this will have todo
                        List<String> tokens = match.repoPath.path.split('/')
                        String stageVersion = tokens[tokens.size() - 2]
                        def releaseRepoPath = getReleaseRepoPath(targetRepository, match.repoPath, stageVersion, snapExp)
                        def releaseFileInfo = repositories.getFileInfo(releaseRepoPath)
                        def moduleInfo = parseModuleId(dep.id, snapExp)
                        releaseDependencies << new Dependency(moduleInfo.id, releaseFileInfo, dep.scopes, dep.type)
                    } else {
                        // external dependency, leave it
                        releaseDependencies << dep
                    }
                }
                dependenciesList.clear()
                dependenciesList.addAll(releaseDependencies)
            }
        } catch (IllegalStateException e) {
            rollback(releaseBuild, releaseArtifactsSet, e.message, e)
        }

        // Add release status
        def statuses = releaseBuild.releaseStatuses
        statuses << new ReleaseStatus("released", 'Releasing build gradle-multi-example', targetRepository, getStringProperty(params, 'ciUser', false), security.currentUsername)
        // Save new DetailedBuildRun (Build info)
        builds.saveBuild(releaseBuild)
        if (releaseArtifactsSet.size() != stageArtifactsList.size()) {
            log.warn "The plugin implementaion don't fit your build, release artifact size is different from the staging number"
            rollback(releaseBuild, releaseArtifactsSet, null)
        }

        message = " Build $buildName/$buildNumber has been successfully promoted"
        log.info message
        status = 200
    }
}

private Map<String, String> parseModuleId(String id, String snapExp) {
    List idTokens = id.split(':')
    String stageVersion = idTokens.pop()
    // Implement version per module logic
    idTokens << extractVersion(stageVersion, snapExp)
    id = idTokens.join(':')
    [id: id, stageVersion: stageVersion]
}

private void rollback(BuildRun releaseBuild, Set<RepoPath> releaseArtifactsSet, String message = 'Rolling back build promotion', Throwable cause, int statusCode = 500) {
    releaseArtifactsSet.each { item ->
        StatusHolder status = repositories.delete(item)
        // now let's delete empty folders
        deletedItemParentDirRepoPath = item.parent
        while (!deletedItemParentDirRepoPath.root && repositories.getChildren(deletedItemParentDirRepoPath).empty) {
            repositories.delete(deletedItemParentDirRepoPath)
            deletedItemParentDirRepoPath = deletedItemParentDirRepoPath.parent
        }
        if (status.error) {
            log.error "Rollback failed! Failed to delete $item, error is $status.statusMsg", status.exception
        } else {
            log.info "$item deleted"
        }
    }
    StatusHolder status = builds.deleteBuild(releaseBuild)
    if (status.error) {
        log.error "Rollback failed! Failed to delete $releaseBuild, error is $status.statusMsg", status.exception
    }
    cancelPromotion(message, cause, statusCode)
}

private RepoPath getReleaseRepoPath(String targetRepository, RepoPath stageRepoPath, String stageVersion, String snapExp) {
    def layoutInfo = repositories.getLayoutInfo(stageRepoPath)
    releaseVersion = extractVersion(stageVersion, snapExp)
    String stagingPath = stageRepoPath.path
    if (layoutInfo.integration || stageVersion =~ ".*" + snapExp + ".*") {
        String releasePath
        // this might not work
        if (layoutInfo.valid) {
            releasePath = stagingPath.replace("-$layoutInfo.folderIntegrationRevision", '')
            // removes -SNAPSHOT from folder name
            releasePath = releasePath.replace("-$layoutInfo.fileIntegrationRevision", '')
            // removes -timestamp from file name
        } else {
            // let's hope the version is simple
            releasePath = stagingPath.replace(stageVersion, releaseVersion)
        }
        if (releasePath == stagingPath) {
            throw new IllegalStateException("Converting stage repository path$stagingPath to released repository path failed, please check your snapshot expression")
        }
        create(targetRepository, releasePath)
    } else {
        log.info "Your build contains release version of $stageRepoPath"
        create(targetRepository, stagingPath)
    }
}

def getStageRepoPath(Artifact stageArtifact, Set<FileInfo> stageArtifactsList) {
    // stageArtifact.name = multi-2.15-SNAPSHOT.pom
    // stageArtifactsList.toArray()[0].name= multi1-2.15-20120503.095917-1-tests.jar
    def tmpArtifact = stageArtifactsList.find {
        def layoutInfo = repositories.getLayoutInfo(it.repoPath)
        // this might not work for repos without layout
        // checking the name won't help - it is  called ivy.xml
        (stageArtifact.type == 'ivy' || !layoutInfo.valid || stageArtifact.name.startsWith(layoutInfo.module)) &&
            it.sha1 == stageArtifact.sha1
    }
    if (tmpArtifact == null) {
        log.warn "No Artifact with the same name and sha1 was found, somthing is wrong with your build info, look for $stageArtifact.name $stageArtifact.sha1 there is probably mor then one artifact with the same sha1"
        return null
    }
    tmpArtifact.repoPath
}

@SuppressWarnings("GroovyAccessibility")
// it complains about Node.parent when I refer to <parent> tag
private StatusHolder generateAndDeployReleasePomFile(RepoPath stageRepoPath, releaseRepoPath, List<FileInfo> innerModuleDependencies, Set<FileInfo> stageArtifactsList, String snapExp) {
    def stagePom = repositories.getStringContent(stageRepoPath)
    def project = new XmlSlurper(false, false).parseText(stagePom)
    if (!project.version.isEmpty()) {
        project.version = extractVersion(project.version.text(), snapExp)
    }
    // also try the parent
    if (!project.parent.version.isEmpty()) {
        project.parent.version = extractVersion(project.parent.version.text(), snapExp)
    }

    innerModuleDependencies.each { FileInfo artifact ->
        def layoutInfo = repositories.getLayoutInfo(artifact.repoPath)
        project.dependencies.dependency.findAll { dependency ->
            dependency.groupId == layoutInfo.organization && dependency.artifactId == layoutInfo.module
        }.each { dependency ->
            dependency.version = extractVersion(dependency.version.isEmpty() ? "${layoutInfo.baseRevision}${layoutInfo.integration ? '-' + layoutInfo.folderIntegrationRevision : ''}" : dependency.version.text(), snapExp)
        }
    }

    repositories.deploy(releaseRepoPath, streamXml(project))
}

private StringInputStream streamXml(xml) {
    String result = new StreamingMarkupBuilder().bind { mkp.yield xml }
    new StringInputStream(serialize(result))
}

// Pars the xml and modify values and deploy
private StatusHolder generateAndDeployReleaseIvyFile(RepoPath stageRepoPath, releaseRepoPath, List<FileInfo> innerModuleDependencies, String snapExp) {
    def stageIvy = repositories.getStringContent(stageRepoPath)
    // stageIvy.replace('m:classifier','classifier')
    def slurper = new XmlSlurper(false, false)
    slurper.keepWhitespace = true
    def releaseIvy = slurper.parseText(stageIvy)
    def info = releaseIvy.info[0]
    def stageRev = info.@revision.text()
    info.@revision = extractVersion(stageRev, snapExp)
    info.@status = 'release'
    // fix date and xml alignment and module dependency
    info.@publication = timestamp
    // Fix inner module dependencies
    innerModuleDependencies.each { art ->
        String[] tokens = art.repoPath.path.split('/')
        def stageVersion = tokens[tokens.size() - 2]
        def name = art.name.split('-')[0]
        def org = tokens[0]
        releaseIvy.dependencies.dependency.findAll { md -> md.@org == org && md.@rev == stageVersion && md.@name == name }.each { e -> e.@rev = extractVersion(stageVersion, snapExp) }
    }

    repositories.deploy(releaseRepoPath, streamXml(releaseIvy))
}

// Copy properties and modify status/timestamp
private void setReleaseProperties(stageRepoPath, releaseRepoPath) {
    def properties = repositories.getProperties(stageRepoPath)
    properties.replaceValues('build.number', ["${properties.getFirst('build.number')}-r"])
    properties.replaceValues('build.status', ['release'])
    properties.replaceValues('build.timestamp', [timestamp])
    def keys = properties.keys()
    keys.each { item ->
        key = item
        def values = properties.get(item)
        values.each { val ->
            repositories.setProperty(releaseRepoPath, key, val)
        }
    }
}

// This is the place to implement the release version expressions logic
def extractVersion(String stageVersion, snapExp) {
    stageVersion.split('-')[0]
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
