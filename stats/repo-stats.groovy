import org.artifactory.repo.RepoPathFactory
import groovy.json.JsonBuilder


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

/** 
 * This plugin do good.
 * 
 * Created with IntelliJ IDEA.
 * User: Itamar Berman-Eshel
 * Date: 7/14/13
 * Time: 2:39 PM
 * To change this template use File | Settings | File Templates.
 */

/*
* curl -X POST -uadmin:password "http://localhost:8081/artifactory/api/plugins/execute/stats?params=repo=REPOKEY"
*/

executions {
    artifactCount() { params ->
        try {
            def json = new JsonBuilder()
            json {
                stats((params['repos'] as List).findResults { repo ->
                    repoPath = RepoPathFactory.create("$repo/")
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




//executions {
//    artifactCount() { params ->
//        try {
//            def repos = params['repos'] as String[]
//            def json = new JsonBuilder()
//            json {
//                stats repos.collect { repo ->
//                    repoPath = RepoPathFactory.create(repo, '/')
//                    if (repositories.exists(repoPath)) {
//                        ["repoKey": repo,
//                                "count": repositories.getArtifactsCount(repoPath),
//                                "size": repositories.getArtifactsSize(repoPath)
//                        ]
//                    } else {
//                        log.warn("Repository $repo does not exist")
//                    }
//                }
//            }
//
//            message = json.toPrettyString()
//
//        } catch (e) {
//            message = e.message
//            status = 500
//        }
//    }
//}




//executions {
//    artifactCount() { params ->
//        try {
//            def repos = params['repos'] as String[]
//            def count = 0
//            def size = 0
//            def totalCount =0
//            def totalSize =0
//            def json = new JsonBuilder()
//            repos.each {
//             String repoKey = it
//             def repoPath = RepoPathFactory.create(repoKey, '/')
//             if (repositories.exists(repoPath)){
//                    count = repositories.getArtifactsCount(repoPath)
//                    size = repositories.getArtifactsSize(repoPath)
//                    log.info "$repoPath has $count artifacts with a total size of $size"
//                    totalCount += count
//                    totalSize += size
//                 json.Repository
//                         {
//                             "Name" repoKey
//                             "Artifact Count" count
//                             "Artifacts Size in kb" size
//                         }
//                }
//                  else log.info "Repository $repoPath does not exist"
//            }
//            log.info "Found $totalCount artifacts with a total size of $totalSize"
//            message = "Found $totalCount artifacts with a total size of $totalSize, Please see the log file for detailed information on each querried repository \n" +json.toPrettyString()
//
//        } catch (e) {
//            message = "Error"
//            status = 500
//        }
//    }
//}