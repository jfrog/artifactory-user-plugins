/*
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2013 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 */
 
 import org.artifactory.repo.RepoPathFactory
 
 import groovy.json.JsonBuilder
 
 /**
 *
 * @author itamarb
 * @since 21/07/13
 */

/** 
 * This execution is named 'repoStats' and it will be called by REST by this name
 * The expected (and mandatory) parameter is comma separated list of repos for which the stats will be be queried
 * curl -X POST -uadmin:password "http://localhost:8081/artifactory/api/plugins/execute/repoStats?params=repo=repo,otherRepo"
 */

executions {
    repoStats() { params ->
        try {
            def json = new JsonBuilder()
            json {
		//create a list of all repositories from the params 
                stats((params['repos'] as List).findResults { repo ->
                    repoPath = RepoPathFactory.create("$repo/")
		    //if the repository exists and was typed correctly, get its artifact count and size and insert to the json
                    if (repositories.exists(repoPath)) {
                        [
                                repoKey: repo,
                                count: repositories.getArtifactsCount(repoPath),
                                size: repositories.getArtifactsSize(repoPath)
                        ]
                    } else {
                        log.warn("Repository $repo does not exist")
                    }
                })
            }
            if (json.content.stats) {
                message = json.toPrettyString()
                status = 200
            } else {
                message = 'no valid repositories found'
                status = 400
            }

        } catch (e) {
            log.error 'Failed to execute plugin', e
            message = e.message
            status = 500
        }
    }
}
