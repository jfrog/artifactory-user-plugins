import org.artifactory.repo.RepoPath

import static java.lang.Thread.sleep
import static org.artifactory.repo.RepoPathFactory.create

/*
* Artifactory is a binaries repository manager.
* Copyright (C) 2012 JFrog Ltd.
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
 *
 * @author jbaruch
 * @since 16/08/12
 */

//this is REST-executed plugin
executions {
    //this execution is named 'deleteEmptyDirs' and it will be called by REST by this name
    //map parameters provide extra information about this execution, such as version, description, users that allowed to call this plugin, etc.
    //The expected (and mandatory) parameter is comma separated list of paths from which empty folders will be searched.
    //curl -X POST -v -u admin:password "http://localhost:8080/artifactory/api/plugins/execute/deleteEmptyDirs?params=paths=repo,otherRepo/some/path"
    deleteEmptyDirs(version: '1.0', description: 'Deletes empty directories', users: ['admin'].toSet()) { params ->
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
    //let's let other threads to do something.
    sleep 50
    //if not folder - we're done, nothing to do here
    if (repositories.getItemInfo(path).folder) {
        def children = repositories.getChildren path
        children.each {
            deleteEmptyDirsRecursively it.repoPath
        }
        //now let's check again
        if (repositories.getChildren(path).empty) {
            //it is folder, and no children - delete!
            repositories.delete path
        }
    }
}