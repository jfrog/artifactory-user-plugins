/*
 * Copyright (C) 2022 JFrog Ltd.
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

import org.artifactory.repo.RepoPath
import org.artifactory.request.Request

final List<String> reposToExpire = List.of(/*TODO: add repo names*/)

download {
    beforeDownloadRequest { Request request, RepoPath repoPath ->
        if (reposToExpire.contains(repoPath.repoKey) && isRemote(repoPath.repoKey)) {
            log.debug 'Repository ${repoPath.repoKey} is marked for file expiration. Expiring file: ${repoPath.name}'
            expired = true
        }
    }
}

def isRemote(String repoKey) {
    return repositories.getRemoteRepositories().contains(repoKey)
}