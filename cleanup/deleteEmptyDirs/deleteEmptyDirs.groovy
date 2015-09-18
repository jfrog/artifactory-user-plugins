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

import org.artifactory.repo.RepoPath

import static java.lang.Thread.sleep
import static org.artifactory.repo.RepoPathFactory.create

/**
 *
 * @author jbaruch
 * @since 16/08/12
 */

// this is REST-executed plugin
executions {
    // this execution is named 'deleteEmptyDirs' and it will be called by REST by this name
    // map parameters provide extra information about this execution, such as version, description, users that allowed to call this plugin, etc.
    // The expected (and mandatory) parameter is comma separated list of paths from which empty folders will be searched.
    // curl -X POST -v -u admin:password "http://localhost:8080/artifactory/api/plugins/execute/deleteEmptyDirsPlugin?params=paths=repo,otherRepo/some/path"
    deleteEmptyDirsPlugin(version: '1.0', description: 'Deletes empty directories', users: ['admin'].toSet()) { params ->
        if (!params || !params.paths) {
            def errorMessage = 'Paths parameter is mandatory, please supply it.'
            log.error errorMessage
            status = 400
            message = errorMessage
        } else {
            params.paths.each {
                deleteEmptyDirsRecursively create(it)
            }
        }
    }
}

def deleteEmptyDirsRecursively(RepoPath path) {
    // let's let other threads to do something.
    sleep 50
    // if not folder - we're done, nothing to do here
    if (repositories.getItemInfo(path).folder) {
        def children = repositories.getChildren path
        children.each {
            deleteEmptyDirsRecursively it.repoPath
        }
        // now let's check again
        if (repositories.getChildren(path).empty) {
            // it is folder, and no children - delete!
            repositories.delete path
        }
    }
}
