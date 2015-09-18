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

import static CacheConstants.JSON_CACHE_MILLIS

class CacheConstants {
    // Cache *.json files for 1 hour
    static final long JSON_CACHE_MILLIS = 3600 * 1000L
}

download {
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
    if (!repositories.exists(repoPath)) {
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
