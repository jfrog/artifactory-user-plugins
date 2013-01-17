import org.artifactory.fs.ItemInfo
import org.artifactory.repo.RepoPath
import org.artifactory.request.Request

import static java.lang.Class.forName

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
    //if the status property not set (legacy and assertion reasons) or the value is not 'approved' (might be 'pending' or 'forbi, return 403.
    //poms should be allowed anyway: the licence might be in the parent poms and they are resolved on the client.
    altResponse { Request request, RepoPath responseRepoPath ->
        def artifactStatus = repositories.getProperties(responseRepoPath).getFirst(APPROVE_STATUS_NAME)
        if (!responseRepoPath.name.endsWith('.pom') && artifactStatus && artifactStatus != APPROVE_STATUS_APPROVED) {
            status = 403
            if (artifactStatus && artifactStatus == APPROVE_STATUS_REJECTED) {
                message = 'This artifact wasn\'t approved yet.'
            } else {
                message = 'This artifact was rejected due to its license.'
            }
            log.warn "You asked for an unapproved artifact: $responseRepoPath. 403 in da face!";
        }
    }
}

storage {
    afterCreate { ItemInfo item ->
        def licensesService = ctx.beanForType(forName('org.artifactory.addon.license.service.InternalLicensesService'))
        RepoPath repoPath = item.repoPath
        def licenses = licensesService.getLicensesForRepoPath(repoPath)

        //set the regular licenses properties
        repositories.setProperty(repoPath, licensesService.LICENSES_PROP_FULL_NAME, *licenses*.name)

        //if one of the licenses is in the forbidden list - end of story, ban it forever
        if (licenses.any { FORBIDDEN_LICENSES.contains(it.name) }) {
            repositories.setProperty(repoPath, APPROVE_STATUS_NAME, APPROVE_STATUS_REJECTED)
        }
        //if not, if one of the licenses is approved, the artifact is approved
        else if (licenses.any { it.approved }) {
            repositories.setProperty(repoPath, APPROVE_STATUS_NAME, APPROVE_STATUS_APPROVED)
        }
        //if not - pending
        else {
            repositories.setProperty(repoPath, APPROVE_STATUS_NAME, APPROVE_STATUS_PENDING)
        }
    }
}