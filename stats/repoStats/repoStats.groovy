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
import org.artifactory.repo.RepoPathFactory

/**
 * This execution is named 'repoStats', and it will be called by REST by this
 * name. The expected (and mandatory) parameter is comma separated list of
 * repoPaths for which the stats will be be queried.
 * curl -X POST -uadmin:password "http://localhost:8081/artifactory/api/plugins/execute/repoStats?params=paths=repoPath,otherRepoPath"
 *
 * @author itamarb
 * @since 21/07/13
 */

executions {
    repoStats() { params ->
        try {
            def json = new JsonBuilder()
            json {
                // create a list of all repositories from the params
                stats((params['paths'] as List).findResults { path ->
                    repoPath = RepoPathFactory.create("$path/")
                    // if the path exists and was typed correctly, get its
                    // artifact count and size and insert to the json
                    if (repositories.exists(repoPath)) {
                        [
                            repoPath: path,
                            count   : repositories.getArtifactsCount(repoPath),
                            size    : repositories.getArtifactsSize(repoPath),
                            sizeUnit: "bytes"
                        ]
                    } else {
                        log.warn("The path $path does not exist")
                    }
                })
            }
            if (json.content.stats) {
                message = json.toPrettyString()
                status = 200
            } else {
                message = 'no valid paths found'
                status = 400
            }
        } catch (e) {
            log.error 'Failed to execute plugin', e
            message = e.message
            status = 500
        }
    }
}
