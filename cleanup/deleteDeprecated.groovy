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

import org.artifactory.repo.RepoPath

import static com.google.common.collect.Multimaps.forMap

/**
 *
 * @author jbaruch
 * @since 08/08/12
 */

//this is REST-executed plugin
//curl -X POST -v -u admin:password "http://localhost:8080/artifactory/api/plugins/execute/deleteDeprecatedPlugin"
executions {
    //this execution is named 'deleteDeprecated' and it will be called by REST by this name
    //map parameters provide extra information about this execution, such as version, description, users that allowed to call this plugin, etc.
    deleteDeprecatedPlugin(version: '1.0', description: 'Deletes artifacts marked with \'analysis.deprecated=true\' property', users: ['admin'].toSet()) {

        //we use the searched object to search for all items annotated with the desired property (hey, IntelliJ IDEA knows about the searches object and provides code completion and analysis!
        // It's thanks to this - https://github.com/JFrogDev/artifactory-user-plugins/blob/master/ArtifactoryUserPlugins.gdsl)
        List<RepoPath> paths = searches.itemsByProperties(forMap(['analysis.deprecated': true.toString()]))
        paths.each {
            //just delete whatever found
            repositories.delete it
            //now let's delete if some directories became empty
            deleteEmptyDirs it.parent
        }
    }

}

def deleteEmptyDirs(RepoPath path) {
    def parent = path.parent
    if (repositories.getChildren(path).empty) {
        repositories.delete path
    }
    if (!parent.root) {
        deleteEmptyDirs parent
    }
}


