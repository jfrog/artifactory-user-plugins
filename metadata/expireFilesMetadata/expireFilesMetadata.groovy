/*
 * Copyright (C) 2018 JFrog Ltd.
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

import java.nio.file.FileSystems
import java.nio.file.PathMatcher
import java.nio.file.Paths

import org.artifactory.fs.ItemInfo
import org.artifactory.repo.RepoPath
import org.artifactory.request.Request

import groovy.transform.Field

@Field final String PROPERTIES_FILE_PATH = "plugins/${this.class.name}.properties"

def config = new ConfigSlurper().parse(new File(ctx.artifactoryHome.haAwareEtcDir, PROPERTIES_FILE_PATH).toURI().toURL())

log.info "Check 'Expire Files Metadata' plugin repositories list: $config.repositories"

def repos = config.repositories.clone()

executions {
    expireFilesMetadataConfig() { params ->
        log.info "Update configuration with parameters: " + params
        def action = params['action'] ? params['action'][0] as String : "add"

        def repositoriesString = "[]"
        if (params['repositories']) {
            repositoriesString = params['repositories'].join(',')
        }
        def configRepositories = new ConfigSlurper().parse('repositories=' + repositoriesString)

        log.info "configRepositories: "+ configRepositories

        if (action == 'reset'){
            log.info "Reseting configuration before adds"
            repos.clear()
        }

        repos = configRepositories.repositories.clone()
    }
}

download {
    beforeDownloadRequest { Request request, RepoPath repoPath ->
        log.info "repoPath: " + repoPath

        repos.each { repo, params ->
            log.info "repo: " + repo
            log.info "params: " + params

            long expire = params[0] * 1000L
            def patterns = params[1]

            log.info "expire = " + expire
            patterns.each { pattern ->
                log.debug repo + ".pattern: " + pattern
                log.debug "repoPath: " + repoPath.path

                PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);

                if (isRemote(repoPath.repoKey) && isGeneric(repoPath.repoKey) && shouldExpire(repoPath, expire)) {
                    if (matcher.matches(Paths.get(repoPath.path))){
                        log.debug "Expiring " + pattern
                        expired = true
                    } else {
                        log.debug "Not expiring " + pattern
                    }
                }
            }
        }
    }
}

def isGeneric(String repoKey) {
    return repositories.getRepositoryConfiguration(repoKey).getPackageType() == 'generic'
}

def isRemote(String repoKey) {
    return repositories.getRemoteRepositories().contains(repoKey)
}

def shouldExpire(RepoPath repoPath, long expire) {

    if (!repositories.exists(repoPath)) {
        return false
    }

    ItemInfo itemInfo = repositories.getItemInfo(repoPath)
    long cacheAge = getCacheAge(itemInfo)

    return cacheAge > expire || cacheAge == -1
}

def getCacheAge(ItemInfo itemInfo) {
    long lastUpdated = itemInfo.lastUpdated
    if (lastUpdated <= 0) {
        return -1
    }
    return System.currentTimeMillis() - lastUpdated
}
