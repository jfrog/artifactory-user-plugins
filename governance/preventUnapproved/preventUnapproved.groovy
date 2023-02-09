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

/**
 *
 * @author jbaruch
 * @since 20/08/12
 */

download {
    altResponse { request, responseRepoPath ->
        def artifactStatus = repositories.getProperties(responseRepoPath).getFirst('approver.status')
        if (artifactStatus && artifactStatus != 'approved') {
            status = 403
            message = 'This artifact wasn\'t approved yet, please use the Approver application.'
            log.warn "You asked for an unapproved artifact: $responseRepoPath. 403 in da face!"
        }
    }
}
