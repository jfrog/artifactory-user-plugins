import org.artifactory.addon.AddonsManager
import org.artifactory.addon.blackduck.generic.model.ExternalComponentInfo
import org.artifactory.addon.blackduck.service.BlackDuckService
import org.artifactory.addon.blackduck.service.impl.CodeCenterServerProxyV6_4_0_Integration
import org.artifactory.addon.wicket.BlackDuckWebAddon
import org.artifactory.fs.ItemInfo
import org.artifactory.mime.MavenNaming
import org.artifactory.repo.RepoPath

import static com.google.common.collect.Multimaps.forMap

/*
 * Copyright 2013 JFrog Ltd.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 *
 * @author fred simon
 * @since 14/01/14
 */

final PROP_NAME_PREFIX = 'blackduck.cc'
final ID_PROP_NAME = PROP_NAME_PREFIX + '.id'
final EXTERNALID_PROP_NAME = PROP_NAME_PREFIX + '.externalid'
final STATUS_PROP_NAME = PROP_NAME_PREFIX + '.status'

enum GeneralStatuses {
    NEW, PENDING, APPROVED, REJECTED, MANUAL_PENDING
}

executions {
    // The BD CC calls the REST API /api/plugins/setStatusProperty?params=id=459201|externalId=:Newtonsoft.Json:5.0.6|status=APPROVED
    setStatusProperty(version: '1.0',
            description:'set a new status value on all files with cc.id or cc.externalid provided',
            httpMethod: 'POST', users: ['blackduck'].toSet(),
            params: [id:'', externalid: '', status: 'APPROVED']) { params ->
        String id = params?.get('id')?.get(0)
        String externalid = params?.get('externalid')?.get(0)
        String ccStatus = params?.get('status')?.get(0)
        log.debug "trying to change cc status of id=$id or externalid=$externalid with status=$ccStatus"
        if (!id && !externalid) {
            status = 400
            message = "A blackduck ID or externalID is needed to set the new status!"
            return
        }
        if (!ccStatus) {
            status = 400
            message = "No value for the new status was provided!"
            return
        }
        if (!GeneralStatuses.values().any {it.name() == ccStatus} ) {
            status = 400
            message = "Status value $ccStatus does not exists in ${GeneralStatuses.values()}!"
            return
        }

        List<RepoPath> found
        if (id) {
            found = searches.itemsByProperties(forMap([ID_PROP_NAME: id]))
        } else {
            found = searches.itemsByProperties(forMap([EXTERNALID_PROP_NAME: externalid]))
        }
        if (!found) {
            status = 404
            message = "No artifacts found with id=$id or externalid=$externalid"
            return
        }
        found.each {
            log.debug "Setting ${STATUS_PROP_NAME}=${ccStatus} on ${it.getId()}"
            repositories.setProperty(it, STATUS_PROP_NAME, ccStatus)
            // TODO: Activate some other workflow like copy or move or sync
        }
    }
}

storage {
    afterCreate { ItemInfo item ->
        RepoPath repoPath = item.repoPath
        if (isRemote(repoPath.repoKey) && !MavenNaming.isMavenMetadata(repoPath.path)) {
            repositories.setProperty(repoPath, STATUS_PROP_NAME, GeneralStatuses.NEW.name())
        }
    }
}

jobs {
    // Create the pending CC requests for all NEW artifacts
    createCodeCenterRequests(interval: 30000, delay: 300000) {
        AddonsManager addonsManager = ctx.getBean(AddonsManager.class)
        BlackDuckWebAddon blackDuckWebAddon = addonsManager.addonByType(BlackDuckWebAddon.class)
        if (!blackDuckWebAddon.isEnableIntegration()) {
            log.error "Blackduck integration configuration not done!"
            return
        }
        log.info "Finding NEW artifacts that needs BlackDuck approval"
        // Here in how to get the CC connection API object
        BlackDuckService bdService = ctx.getBean(BlackDuckService.class)
        CodeCenterServerProxyV6_4_0_Integration ccConn = bdService.blackDuckWSProvider.blackDuckConnectionProvider.getCodeCenterConnection()
        List<RepoPath> paths = searches.itemsByProperties(forMap([STATUS_PROP_NAME: GeneralStatuses.NEW.name()]))
        paths.each { RepoPath newArtifact ->
            // Be careful that all POM and JARS for the same GAV are retrieved here
            ExternalComponentInfo eci = bdService.getExternalComponentInfo(newArtifact)
            // TODO: Look at the created blackduck.cc properties and create the general request
            // After creating the request set the status to PENDING
            repositories.setProperty(newArtifact, STATUS_PROP_NAME, GeneralStatuses.NEW.name())
        }
    }
}

def isRemote(String repoKey) {
    return repositories.getRemoteRepositories().contains(repoKey)
}
