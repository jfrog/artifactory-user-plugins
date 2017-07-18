import org.artifactory.fs.ItemInfo
import org.artifactory.repo.RepoPath
import org.artifactory.request.Request

import static CacheConstants.PACKAGES_GZ_CACHE_MILLIS

/*
 * Copyright (C) 2016 JFrog Ltd.
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

class CacheConstants {
    static long PACKAGES_GZ_CACHE_MILLIS = 1800 * 1000L
}

executions {
    /*
     *   This execution provide the ability to change the cache time.
     *   It is used by the test script to set a value lower than the default so this plugin
     *   can be automatically tested in an acceptable time.
     */
    setPackageMetadataCacheTime (params: ['cache_millis':'1800']) { params ->
        CacheConstants.PACKAGES_GZ_CACHE_MILLIS = (params['cache_millis'][0] as Long) * 1000L
        log.warn "PACKAGES.gz cache time set to $CacheConstants.PACKAGES_GZ_CACHE_MILLIS millis"
    }
}

// This plugin will expire files called PACKAGES.gz when they are requested if the remote file is older.
// This can fix issues with CRAN metadata sync and with Debian metadata
download {
    beforeDownloadRequest { Request request, RepoPath repoPath ->
        if (repoPath.path.endsWith("PACKAGES.gz") || repoPath.path.endsWith("Packages.gz")){
            if (isRemote(repoPath.repoKey) && shouldExpire(repoPath)) {
                log.warn 'DEBUG: Expiring PACKAGES.gz'
                expired = true
            } else {
                log.warn 'DEBUG: Not expiring PACKAGES.gz'
            }
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

    ItemInfo itemInfo = repositories.getItemInfo(repoPath)
    long cacheAge = getCacheAge(itemInfo)
    return cacheAge > PACKAGES_GZ_CACHE_MILLIS || cacheAge == -1
}

def getCacheAge(ItemInfo itemInfo) {
    long lastUpdated = itemInfo.lastUpdated
    if (lastUpdated <= 0) {
        return -1
    }
    return System.currentTimeMillis() - lastUpdated
}