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
 * The expected (and mandatory) parameter is comma separated list of repoPaths for which the stats will be be queried
 * curl -X POST -uadmin:password "http://localhost:8081/artifactory/api/plugins/execute/repoStats?params=paths=repoPath,otherRepoPath"
 */

executions {
    repoStats() { params ->
        try {
            def json = new JsonBuilder()
            json {
		//create a list of all repositories from the params 
                stats((params['paths'] as List).findResults { path ->
                    repoPath = RepoPathFactory.create("$path/")
		    //if the path exists and was typed correctly, get its artifact count and size and insert to the json
                    if (repositories.exists(repoPath)) {
                        [
                                repoPath: path,
                                count: repositories.getArtifactsCount(repoPath),
                                size: repositories.getArtifactsSize(repoPath)
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
