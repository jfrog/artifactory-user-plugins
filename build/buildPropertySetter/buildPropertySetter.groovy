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
import org.artifactory.build.DetailedBuildRun
import org.artifactory.repo.RepoPath

import static com.google.common.collect.Multimaps.forMap

/**
 * This plugin show tagging all files published by a build with latest=true
 * whenever a new build arrives
 */

build {
    afterSave { DetailedBuildRun buildRun ->
        if (shouldActivateLatest(buildRun)) {
            log.info("Build ${buildRun.getName()}:${buildRun.getNumber()}" +
                     " artifacts set to latest")
            // First remove all latest=true flags for same build name
            searches.itemsByProperties(forMap([
                'build.name': buildRun.getName(),
                'latest'    : 'true'
            ])).each { RepoPath previousLatest ->
                log.debug("Artifact ${previousLatest.getId()} removed from" +
                          " latest")
                repositories.deleteProperty(previousLatest, 'latest')
            }
            // Set the property latest=true on all relevant artifacts
            // This means same properties build.name and build.number and sha1
            // present
            Set<String> publishedHashes = new HashSet<>()
            buildRun.modules?.each {
                it.artifacts?.each { publishedHashes << it.sha1 }
            }
            searches.itemsByProperties(forMap([
                'build.name'  : buildRun.getName(),
                'build.number': buildRun.getNumber()
            ])).each { RepoPath published ->
                def info = repositories.getFileInfo(published)
                if (publishedHashes.contains(info.sha1)) {
                    // Same props and hash change property
                    log.debug "Artifact ${published.getId()} set to latest"
                    repositories.setProperty(published, 'latest', 'true')
                }
            }
        }
    }
}

boolean shouldActivateLatest(BuildRun buildRun) {
    log.debug("Evaluating if build ${buildRun.getName()}:" +
              "${buildRun.getNumber()} should be set to latest!")
    true
}
