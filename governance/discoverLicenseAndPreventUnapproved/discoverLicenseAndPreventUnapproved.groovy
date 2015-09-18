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

import com.google.common.collect.HashMultimap
import org.artifactory.addon.license.LicensesAddon
import org.artifactory.fs.ItemInfo
import org.artifactory.repo.RepoPath
import org.artifactory.request.Request

import static java.lang.Class.forName

/**
 *
 * @author jbaruch
 * @since 17/01/13
 */

final FORBIDDEN_LICENSES = ['GPL-2.0', 'CC BY-SA']
final APPROVE_STATUS_NAME = 'approve.status'
final APPROVE_STATUS_APPROVED = 'approved'
final APPROVE_STATUS_PENDING = 'pending'
final APPROVE_STATUS_REJECTED = 'rejected'

download {
    // if the status property not set (legacy and assertion reasons) or the value is not 'approved' (might be 'pending' or 'forbi, return 403.
    // poms should be allowed anyway: the licence might be in the parent poms and they are resolved on the client.
    altResponse { Request request, RepoPath responseRepoPath ->
        def artifactStatus = repositories.getProperties(responseRepoPath).getFirst(APPROVE_STATUS_NAME)
        if (!responseRepoPath.name.endsWith('.pom') && (!artifactStatus || artifactStatus != APPROVE_STATUS_APPROVED)) {
            status = 403
            if (artifactStatus && artifactStatus == APPROVE_STATUS_REJECTED) {
                message = 'This artifact wasn\'t approved yet.'
            } else {
                message = 'This artifact was rejected due to its license.'
            }
            log.warn "You asked for an unapproved artifact: $responseRepoPath. 403 in da face!"
        }
    }
}

storage {
    afterCreate { ItemInfo item ->
        if (!repositories.getRepositoryConfiguration(item.getRepoKey()).getType().equals("virtual")) {
            def licensesService = ctx.beanForType(forName('org.artifactory.addon.license.service.InternalLicensesService'))
            RepoPath repoPath = item.repoPath
            def props = new HashMultimap()
            def properties = repositories.getProperties(repoPath)
            for (def key : properties.keySet()) {
                for (def v : properties.get(key)) {
                    if (key == 'artifactory.licenses' && v == null) props.put(key, '')
                    else props.put(key, v)
                }
            }
            def licenses = licensesService.getLicensesForRepoPath(repoPath, true, true, null, props)*.getLicense()

            // set the regular licenses properties
            def licensesPropName
            if (licensesService.hasProperty('LICENSES_PROP_FULL_NAME'))
                licensesPropName = licensesService.LICENSES_PROP_FULL_NAME
            else licensesPropName = LicensesAddon.LICENSES_PROP_FULL_NAME
            repositories.setProperty(repoPath, licensesPropName, *licenses*.name)

            // if one of the licenses is in the forbidden list - end of story, ban it forever
            if (licenses.any { FORBIDDEN_LICENSES.contains(it.name) }) {
                repositories.setProperty(repoPath, APPROVE_STATUS_NAME, APPROVE_STATUS_REJECTED)
            }
            // if not, if one of the licenses is approved, the artifact is approved
            else if (licenses.any { it.approved }) {
                repositories.setProperty(repoPath, APPROVE_STATUS_NAME, APPROVE_STATUS_APPROVED)
            }
            // if not - pending
            else {
                repositories.setProperty(repoPath, APPROVE_STATUS_NAME, APPROVE_STATUS_PENDING)
            }
        }
    }
}
