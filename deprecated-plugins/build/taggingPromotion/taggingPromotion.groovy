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

import org.artifactory.build.BuildRun
import org.artifactory.common.StatusHolder
import org.artifactory.exception.CancelException
import org.artifactory.fs.FileInfo
import org.artifactory.repo.RepoPath

import static org.artifactory.repo.RepoPathFactory.create

/**
 * This plugin show tagging and promoting war files which can later be resolved
 * by one generic url that filter resolution with matrix params
 */

promotions {
    cloudPromote(users: "jenkins", params: ['aol.staging': '', 'aol.oss': '', 'aol.prod': '', targetRepository: 'cloud-deploy-local']) { buildName, buildNumber, params ->
        log.info 'Promoting build: ' + buildName + '/' + buildNumber

        def properties = [:]
        addStagingProperty(params, properties, 'staging')
        addStagingProperty(params, properties, 'oss')
        addStagingProperty(params, properties, 'prod')
        String targetRepo = getStringProperty(params, 'targetRepository', true)

        if (!params || !buildNumber || properties.isEmpty()) {
            message = 'Please supply build number parameter and at list one aol. parameter .'
            log.error message
            status = 400
            throw new CancelException(message, status)
        }

        List<BuildRun> buildsRun = builds.getBuilds(buildName, buildNumber, null)
        if (buildsRun.size() > 1) {
            cancelPromotion('Found two matching build to promote, please provide build start time', null, 409)
        }

        def buildRun = buildsRun[0]
        if (buildRun == null) {
            cancelPromotion("Build $buildName/$buildNumber was not found, canceling promotion", null, 409)
        }
        Set<FileInfo> stageArtifactsList = builds.getArtifactFiles(buildRun)
        List<RepoPath> targetList = []
        StatusHolder cstatus
        stageArtifactsList.each { item ->
            RepoPath repoPath = item.getRepoPath()
            if (repoPath.getPath().endsWith('.war')) {
                RepoPath targetRepoPath = create(targetRepo, repoPath.getPath())
                targetList << targetRepoPath
                if (!repositories.exists(targetRepoPath)) {
                    cstatus = repositories.copy(repoPath, targetRepoPath)
                    if (cstatus.isError()) {
                        targetList.each { repositories.delete(it) }
                        message = "Copy of $repoPath failed ${cstatus.getLastError().getMessage()}"
                        cancelPromotion(message, cstatus.getLastError().getException(), 500)
                    }
                }
                properties.each { prop ->
                    repositories.setProperty(targetRepoPath, prop.key, prop.value)
                }
            }
        }
    }
}

private addStagingProperty(params, props, String pName) {
    String aolP = getStringProperty(params, pName, false)
    if (aolP) {
        props["aol.$pName".toString()] = aolP
    }
}

private String getStringProperty(params, pName, mandatory) {
    def key = params[pName]
    def val = key == null ? null : key[0].toString()
    if (mandatory && val == null) {
        cancelPromotion("$pName is mandatory paramater", null, 400)
    }
    return val
}

def cancelPromotion(message, Throwable cause, int errorLevel) {
    log.warn message
    throw new CancelException(message, cause, errorLevel)
}
