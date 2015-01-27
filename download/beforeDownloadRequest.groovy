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
import org.artifactory.fs.FileInfo
import org.artifactory.repo.RepoPath
import org.artifactory.request.Request

class CacheConstants {
    // Cache *.json files for 1 hour
    static final long JSON_CACHE_MILLIS = 3600 * 1000L
}

import static CacheConstants.JSON_CACHE_MILLIS

download {
    /**
     * Handle before any download events, at this point the request passed all of Artifactory's filters (authentication etc) and is about to reach the repositories.
     *
     * Context variables:
     * expired (boolean) - Mark the requested resource as expired. Defaults to false (unset).
     *                     An expired resource is one that it's (now() - (last updated time)) time is higher than the repository retrieval cache period milliseconds.
     *                     Setting this option to true should be treated with caution, as it means both another database hit (for updating the last updated time)
     *                     as well as network overhead since if the resource is expired, a remote download will occur to re-download it to the cache.
     *                     A common implementation of this extension point is to check if the resource comply with a certain pattern (for example: a *.json file)
     *                     AND the original request was to the remote repository (and not directly to it's cache)
     *                     AND a certain amount of time has passed since the last expiry check (to minimize DB hits).
     *
     * Closure parameters:
     * request (org.artifactory.request.Request) - a read-only parameter of the request.
     * repoPath (org.artifactory.repo.RepoPath) -  a read-only parameter of the response RepoPath (containing the
     *                                                    physical repository the resource was found in).
     */
    beforeDownloadRequest { Request request, RepoPath repoPath ->
        if (repoPath.path.endsWith(".json") && isRemote(repoPath.repoKey) && shouldExpire(repoPath)) {
            log.info 'Expiring json file: ${repoPath.name}'
            expired = true
        }
    }
}

def isRemote(String repoKey) {
    return repositories.getRemoteRepositories().contains(repoKey)
}

def shouldExpire(RepoPath repoPath) {
    if(!repositories.exists(repoPath)) {
	    return false
	}
	
    FileInfo fileInfo = repositories.getFileInfo(repoPath)
    long cacheAge = getCacheAge(fileInfo)

    return cacheAge > JSON_CACHE_MILLIS || cacheAge == -1
}

def getCacheAge(FileInfo fileInfo) {
    long lastUpdated = fileInfo.lastUpdated
    if (lastUpdated <= 0) {
        return -1
    }
    return System.currentTimeMillis() - lastUpdated
}
