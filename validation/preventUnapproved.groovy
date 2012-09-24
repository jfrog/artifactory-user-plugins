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
 * @since 20/08/12
 */

download {
    altResponse { request, responseRepoPath ->
        def artifactStatus = repositories.getProperties(responseRepoPath).getFirst('approver.status')
        if (artifactStatus  && artifactStatus != 'approved'){
            status = 403
            message = 'This artifact wasn\'t approved yet, please use the Approver application.'
            log.warn "You asked for an unapproved artifact: $responseRepoPath. 403 in da face!";
        }
    }
}