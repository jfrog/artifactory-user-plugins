import org.artifactory.addon.AddonsManager
import org.artifactory.addon.blackduck.generic.model.ExternalComponentInfo
import org.artifactory.addon.blackduck.service.BlackDuckApplicationService
import org.artifactory.addon.blackduck.service.BlackDuckService
import org.artifactory.addon.blackduck.service.impl.BlackDuckRequestInfo
import org.artifactory.addon.blackduck.service.impl.BlackDuckUpdateResult
import org.artifactory.addon.blackduck.service.impl.CodeCenterServerProxyV6_4_0_Integration
import org.artifactory.addon.blackduck.service.impl.Utils
import org.artifactory.addon.wicket.BlackDuckWebAddon
import org.artifactory.api.license.LicenseInfo
import org.artifactory.api.module.ModuleInfo
import org.artifactory.api.repo.RepositoryService
import org.artifactory.fs.ItemInfo
import org.artifactory.mime.MavenNaming
import org.artifactory.repo.RepoPath
import org.jfrog.build.api.BlackDuckProperties
import org.jfrog.build.api.Build
import org.jfrog.build.api.Governance

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

final CC_PROP_NAME_PREFIX = 'blackduck.cc'
final GLOBAL_CC_APP_NAME = 'GlobalApp'
final GLOBAL_CC_APP_VERSION = '1.0'
final ID_PROP_NAME = CC_PROP_NAME_PREFIX + '.id'
final EXTERNALID_PROP_NAME = CC_PROP_NAME_PREFIX + '.externalid'
final STATUS_PROP_NAME = CC_PROP_NAME_PREFIX + '.status'
final REQUEST_ID_PROP_NAME = CC_PROP_NAME_PREFIX + '.' + GLOBAL_CC_APP_NAME + '.' + GLOBAL_CC_APP_VERSION + '.requestId'
final ERROR_PROP_NAME = CC_PROP_NAME_PREFIX + '.' + GLOBAL_CC_APP_NAME + '.' + GLOBAL_CC_APP_VERSION + '.error'
final String[] otherProps = ['riskLevel','vulnerabilities']

enum GeneralStatuses {
    NEW, PENDING, APPROVED, REJECTED, MANUAL_PENDING, ERROR
}

executions {
    // The BD CC calls the REST API /api/plugins/execute/setStatusProperty?params=id=459201|externalId=:Newtonsoft.Json:5.0.6|status=APPROVED
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
        if (GeneralStatuses.values().any {it.name() == ccStatus} ) {
            log.debug "Found valid status $ccStatus"
        } else {
            status = 400
            message = "Status value $ccStatus does not exists in ${GeneralStatuses.values()}!"
            return
        }

        def filter = [:]
        if (id) {
            filter.put(ID_PROP_NAME, id)
        } else {
            filter.put(EXTERNALID_PROP_NAME, externalid)
        }
        List<RepoPath> found = searches.itemsByProperties(forMap(filter))
        if (!found) {
            status = 404
            message = "No artifacts found with id=$id or externalid=$externalid"
            return
        }
        List<String> results = ['Converted BEGIN']
        found.each { RepoPath repoPath ->
            log.debug "Setting ${STATUS_PROP_NAME}=${ccStatus} on ${repoPath.getId()}"
            // Approval activate some other workflow like copy or move or sync => always last
            otherProps.each { String propName ->
                def propVal = params?.get(propName)?.get(0) as String
                if (propVal) {
                    repositories.setProperty(repoPath, CC_PROP_NAME_PREFIX + '.' + propName, propVal)
                }
            }
            repositories.setProperty(repoPath, STATUS_PROP_NAME, ccStatus)
            results << "${repoPath.getId()}:$ccStatus"
        }
        results << 'Converted END\n'
        status = 200
        message = results.join("\n")
    }
}

storage {
    afterCreate { ItemInfo item ->
        try {
            RepoPath repoPath = item.repoPath
            // Activate Code Center workflow only on actual artifacts not pom
            if (!item.folder && isRemote(repoPath.repoKey) &&
                    !MavenNaming.isMavenMetadata(repoPath.path) &&
                    !MavenNaming.isPom(repoPath.path)
            ) {
                log.debug "Setting status=NEW on ${repoPath.getId()}"
                repositories.setProperty(repoPath, STATUS_PROP_NAME, GeneralStatuses.NEW.name())
            }
        } catch (Exception e) {
            log.error("Could not set property on $item", e)
        }
    }
}

jobs {
    // Create the pending CC requests for all NEW artifacts
    createCodeCenterRequests(interval: 300000, delay: 300000) {
        AddonsManager addonsManager = ctx.getBean(AddonsManager.class)
        BlackDuckWebAddon blackDuckWebAddon = addonsManager.addonByType(BlackDuckWebAddon.class)
        if (!blackDuckWebAddon.isEnableIntegration()) {
            log.error "Blackduck integration configuration not done!"
            return
        }
        log.debug "Finding NEW artifacts that needs BlackDuck approval"
        // Here in how to get the CC connection API object
        BlackDuckApplicationService bdAppService = ctx.getBean(BlackDuckApplicationService.class)
        BlackDuckService bdService = ctx.getBean(BlackDuckService.class)
        RepositoryService repoService = ctx.getBean(RepositoryService.class)
        //CodeCenterServerProxyV6_4_0_Integration ccConn = bdService.blackDuckWSProvider.blackDuckConnectionProvider.getCodeCenterConnection()
        def filter = [:]
        filter.put(STATUS_PROP_NAME, GeneralStatuses.NEW.name())
        List<RepoPath> paths = searches.itemsByProperties(forMap(filter))
        Map<String,BlackDuckRequestInfo> requestsByGav = [:]
        paths.each { RepoPath newArtifact ->
            // Be careful that all POM and JARS for the same GAV are retrieved here
            log.debug "Found new artifact ${newArtifact.getId()} that needs approval!"
            ModuleInfo moduleInfo = repoService.getItemModuleInfo(newArtifact);
            String gav = Utils.getGav(moduleInfo);
            if (!requestsByGav.containsKey(gav)) {
                ExternalComponentInfo eci = bdService.getExternalComponentInfo(newArtifact)
                def req = new BlackDuckRequestInfo();
                req.published = false
                req.repoPath = newArtifact
                req.license = LicenseInfo.UNKNOWN.getName()
                req.gav = gav
                req.componentName = moduleInfo.getModule();
                req.componentVersion = moduleInfo.getBaseRevision();
                if (eci?.componentId) {
                    req.componentId = eci.componentId
                    req.componentName = eci.name
                    req.componentVersion = eci.version
                    if (eci.licenses) {
                        req.license = eci.licenses[0]
                    }
                }
                req.status = BlackDuckRequestInfo.Status.Missing
                requestsByGav.put(gav, req)
            }
        }
        if (requestsByGav) {
            def build = new Build()
            build.name = GLOBAL_CC_APP_NAME
            build.number = "${System.currentTimeMillis()}"
            build.setGovernance(new Governance())
            def blackDuckProperties = new BlackDuckProperties()
            blackDuckProperties.appName = GLOBAL_CC_APP_NAME
            blackDuckProperties.appVersion = GLOBAL_CC_APP_VERSION
            blackDuckProperties.includePublishedArtifacts = false
            blackDuckProperties.autoCreateMissingComponentRequests = true
            blackDuckProperties.autoDiscardStaleComponentRequests = false
            build.governance.blackDuckProperties = blackDuckProperties

            log.debug "Updating ${requestsByGav.size()} requests of $GLOBAL_CC_APP_NAME:$GLOBAL_CC_APP_VERSION"
            def res = bdAppService.updateRequests(build, requestsByGav.values() as List)

            // After creating the requests set the status to PENDING or ERROR
            res.each { BlackDuckUpdateResult result ->
                def repoPath = result.requestInfo.repoPath
                if (result.updateStatus == BlackDuckUpdateResult.UpdateStatus.create) {
                    log.debug "Setting status=PENDING requestId=${result.message} on ${repoPath.getId()}"
                    repositories.setProperty(repoPath, STATUS_PROP_NAME, GeneralStatuses.PENDING.name())
                    repositories.setProperty(repoPath, REQUEST_ID_PROP_NAME, result.message)
                } else {
                    log.error "Setting status=ERROR error=${result.message} on ${repoPath.getId()}"
                    repositories.setProperty(repoPath, STATUS_PROP_NAME, GeneralStatuses.ERROR.name())
                    repositories.setProperty(repoPath, ERROR_PROP_NAME, result.message)
                }
            }
        }
    }
}

def isRemote(String repoKey) {
    if (repoKey.endsWith('-cache')) repoKey = repoKey.substring(0,repoKey.length() - 6)
    return repositories.getRemoteRepositories().contains(repoKey)
}
