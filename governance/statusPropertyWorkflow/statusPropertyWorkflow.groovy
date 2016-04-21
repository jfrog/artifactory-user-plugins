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

import org.artifactory.addon.AddonsManager
import org.artifactory.addon.blackduck.generic.model.ExternalComponentInfo
import org.artifactory.addon.blackduck.service.BlackDuckApplicationService
import org.artifactory.addon.blackduck.service.BlackDuckService
import org.artifactory.addon.blackduck.service.impl.BlackDuckComponentCoordinatesService
import org.artifactory.addon.blackduck.service.impl.BlackDuckRequestInfo
import org.artifactory.addon.blackduck.service.impl.BlackDuckUpdateResult
import org.artifactory.addon.blackduck.BlackDuckAddon
import org.artifactory.api.license.LicenseInfo
import org.artifactory.fs.ItemInfo
import org.artifactory.mime.MavenNaming
import org.artifactory.repo.RepoPath
import org.jfrog.build.api.BlackDuckProperties
import org.jfrog.build.api.Build
import org.jfrog.build.api.Governance

import static com.google.common.collect.Multimaps.forMap

/**
 *
 * @author fred simon
 * @since 14/01/14
 */

/**
 ************************************************************************************
 * NOTE!!! This code makes use of non-advertized APIs, and may break in the future! *
 ************************************************************************************
 */

final CC_PROP_NAME_PREFIX = 'blackduck.cc'
final GLOBAL_CC_APP_NAME = 'GlobalApp'
final GLOBAL_CC_APP_VERSION = '1.0'
final ID_PROP_NAME = CC_PROP_NAME_PREFIX + '.id'
final EXTERNALID_PROP_NAME = CC_PROP_NAME_PREFIX + '.externalid'
final STATUS_PROP_NAME = CC_PROP_NAME_PREFIX + '.status'
final REQUEST_ID_PROP_NAME = CC_PROP_NAME_PREFIX + '.' + GLOBAL_CC_APP_NAME + '.' + GLOBAL_CC_APP_VERSION + '.requestId'
final ERROR_PROP_NAME = CC_PROP_NAME_PREFIX + '.' + GLOBAL_CC_APP_NAME + '.' + GLOBAL_CC_APP_VERSION + '.error'
final String[] otherProps = ['riskLevel', 'vulnerabilities']

enum GeneralStatuses {
    NEW, PENDING, APPROVED, REJECTED, MANUAL_PENDING, ERROR
}

executions {
    // The BD CC calls the REST API /api/plugins/execute/setStatusProperty?params=id=459201|externalId=:Newtonsoft.Json:5.0.6|status=APPROVED
    setStatusProperty(version: '1.0',
        description: 'set a new status value on all files with CodeCenter id or externalId provided',
        httpMethod: 'POST', users: ['blackduck'].toSet(),
        params: [id: '', externalid: '', status: 'APPROVED']) { params ->
        String id = params?.get('id')?.get(0)
        String externalId = params?.get('externalId')?.get(0)
        String ccStatus = params?.get('status')?.get(0)
        log.debug "trying to change cc status of id=$id or externalId=$externalId with status=$ccStatus"
        if (!id && !externalId) {
            status = 400
            message = "A BlackDuck CodeCenter id or externalId is needed to set the new status!"
            return
        }
        if (!ccStatus) {
            status = 400
            message = "No value for the new status was provided!"
            return
        }
        if (GeneralStatuses.values().any { it.name() == ccStatus }) {
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
            if (externalId.contains("%")) {
                externalId = org.artifactory.util.HttpUtils.decodeUri(externalId)
            }
            filter.put(EXTERNALID_PROP_NAME, externalId)
        }
        List<RepoPath> found = searches.itemsByProperties(forMap(filter))
        if (!found) {
            status = 404
            message = "No artifacts found with id=$id or externalId=$externalId"
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
        AddonsManager addonsManager = ctx.beanForType(AddonsManager.class)
        BlackDuckAddon blackDuckAddon = addonsManager.addonByType(BlackDuckAddon.class)
        if (!blackDuckAddon.isEnableIntegration()) {
            log.error "Blackduck integration configuration not done!"
            return
        }
        log.debug "Finding NEW artifacts that needs BlackDuck approval"
        // Here in how to get the CC connection API object
        BlackDuckApplicationService bdAppService = ctx.beanForType(BlackDuckApplicationService.class)
        BlackDuckComponentCoordinatesService bdCoordService = ctx.beanForType(BlackDuckComponentCoordinatesService.class)
        BlackDuckService bdService = ctx.beanForType(BlackDuckService.class)
        def filter = [:]
        filter.put(STATUS_PROP_NAME, GeneralStatuses.NEW.name())
        List<RepoPath> paths = searches.itemsByProperties(forMap(filter))
        Map<String, BlackDuckRequestInfo> requestsByGav = [:]
        paths.each { RepoPath newArtifact ->
            // Be careful that all POM and JARS for the same GAV are retrieved here
            log.debug "Found new artifact ${newArtifact.getId()} that needs approval!"
            def coords = bdCoordService.getComponentCoordinates(newArtifact)
            String gav = coords.coordinates
            if (!requestsByGav.containsKey(gav)) {
                ExternalComponentInfo eci = bdService.getExternalComponentInfo(newArtifact)
                def layoutInfo = repositories.getLayoutInfo(newArtifact)
		def req = new BlackDuckRequestInfo()
                req.published = false
                req.repoPath = newArtifact
		req.license = "Unknown"
                req.componentCoordinates = coords	
                req.componentName = layoutInfo.getModule()
                req.componentVersion = layoutInfo.getBaseRevision()
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

            // After creating the requests set the status to PENDING or FAILED_EXECUTION
            res.each { BlackDuckUpdateResult result ->
                def repoPath = result.requestInfo.repoPath
                if (result.updateStatus == BlackDuckUpdateResult.UpdateStatus.create) {
                    log.debug "Setting status=PENDING requestId=${result.message} on ${repoPath.getId()}"
                    repositories.setProperty(repoPath, STATUS_PROP_NAME, GeneralStatuses.PENDING.name())
                    repositories.setProperty(repoPath, REQUEST_ID_PROP_NAME, result.message)
                } else {
                    log.error "Setting status=FAILED_EXECUTION error=${result.message} on ${repoPath.getId()}"
                    repositories.setProperty(repoPath, STATUS_PROP_NAME, GeneralStatuses.ERROR.name())
                    repositories.setProperty(repoPath, ERROR_PROP_NAME, result.message)
                }
            }
        }
    }
}

def isRemote(String repoKey) {
    if (repoKey.endsWith('-cache')) repoKey = repoKey.substring(0, repoKey.length() - 6)
    return repositories.getRemoteRepositories().contains(repoKey)
}
