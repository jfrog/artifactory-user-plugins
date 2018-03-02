/*
 * Copyright (C) 2018 JFrog Ltd.
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

import org.artifactory.exception.CancelException
import org.artifactory.repo.*

def checkedRepositories = ["test-generic-local", "docker"]

storage {
    beforeDelete { item ->
        if(item.repoPath.repoKey in checkedRepositories && !security.isAdmin()) //cancel plugin if user is admin, or repo is not in tested list
        {
            namespaceRepoPath = getNamespaceRepoPath(item.repoPath)
            if(!userIsAuthorized(namespaceRepoPath))
                throw new CancelException("No owner has beeen set for namespace: "+namespacePath+" so deletion is not permitted", 403)
        }
        else
            log.trace("Sandbox plugin not relevant.  User is admin or repository is not in list.")
    }

    beforeCreate { item ->
        if(item.repoPath.repoKey in checkedRepositories)
        {
            namespaceRepoPath = getNamespaceRepoPath(item.repoPath)
            if(!userIsAuthorized(namespaceRepoPath)) //We are now in the use case where a new namespace is being created
            {
                repositories.setProperty(namespaceRepoPath,ownerUsersProp(),security.currentUsername)
            }
        }
    }

    beforePropertyCreate { item, name, values ->
        if(item.repoPath.repoKey in checkedRepositories && !security.isAdmin())
        {
            log.trace("sandbox beforePropertyCreate: "+name)
            checkPropChangeSandboxAuthorization(item, name)
        }
    }

    beforePropertyDelete { item, name ->
        log.trace("sandbox beforePropertyDelete: "+name+" with user: "+security.currentUsername)
        if(item.repoPath.repoKey in checkedRepositories && !security.isAdmin())
        {
            log.trace("sandbox beforePropertyDelete: "+name)
            checkPropChangeSandboxAuthorization(item, name)
        }
    }

}

def checkPropChangeSandboxAuthorization(item, name) {
    if(name==ownerUsersProp() || name==ownerGroupsProp()) //only valid if its one of the sandboxPermissions properties
    {
        namespaceRepoPath = getNamespaceRepoPath(item.repoPath)
        userIsAuthorized(namespaceRepoPath)
    }
}

def getNamespaceRepoPath(RepoPath itemRepoPath)
//Gets the namespace, cancels if we are attempting to operate in root of repository
{
    def repoPathFactory = new RepoPathFactory()
    namespacePath = itemRepoPath.path.split('/')[0]
    log.trace("sandboxPermissions is operating on namespace: "+namespacePath)
    namespaceRepoPath=repoPathFactory.create(itemRepoPath.repoKey,namespacePath)
    if(repositories.getItemInfo(namespaceRepoPath).isFolder())
        return namespaceRepoPath
    else
        throw new CancelException("Only admin users can impact the root path in this repository.", 403)
}

def String propPrefix() {
    return "sandboxPerms"
}
def String propOwnerUsers() {
    return "ownerUsers"
}
def String propOwnerGroups() {
    return "ownerGroups"
}
def String ownerUsersProp() {
    return propPrefix()+"."+propOwnerUsers()
}
def String ownerGroupsProp() {
    return propPrefix()+"."+propOwnerGroups()
}

def Boolean userIsAuthorized(RepoPath namespaceRepoPath)
// Throws the CancelException if user is unauthorized, true if they are, false if the property doesn't exist
{ 
    if(repositories.hasProperty(namespaceRepoPath,ownerUsersProp())) {
        ownerUsersList=repositories.getProperty(namespaceRepoPath,ownerUsersProp()).split('/')
        if (!(security.currentUsername in ownerUsersList))
        {
            log.info("User "+security.currentUsername +" Not Found in list:"+ownerUsersList.toString())
            if(repositories.hasProperty(namespaceRepoPath,ownerGroupsProp()))
            {
                ownerGroupsList=repositories.getProperty(namespaceRepoPath,ownerGroupsProp()).split('/')
                userGroups=security.getCurrentUserGroupNames()
                isGroupInList=false
                ownerGroupsList.each { group ->
                    if(group in userGroups) {
                       isGroupInList=true
                       log.trace("Found Group")
                   }
                }
                if(!isGroupInList) {
                    log.info("User "+security.currentUsername+"contains groups: "+userGroups.toString()+" which is not in allowed list: "+ownerGroupsList.toString())
                    throw new CancelException(security.currentUsername+" does not have permissions to create modify or delete objects in namespace: "+namespacePath, 403)
                }
                else {
                    log.trace("Sandbox permissions granted based on group.")
                    return true
                }
            } 
            else
                throw new CancelException(security.currentUsername+" does not have permissions to create modify or delete objects in namespace: "+namespacePath, 403)
        } 
        else {
            log.trace("Sandbox permissions granted based on user")
            return true
        }
    } 
    else 
        return false
}