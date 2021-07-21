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
import org.artifactory.resource.ResourceStreamHandle

import groovy.json.JsonSlurper
import groovy.transform.Field

@Field final String CONFIG_FILE_PATH = "plugins/${this.class.name}.json"
@Field config

executions {
    expireFilesMetadataConfig() { params, ResourceStreamHandle body ->

        def reader = new InputStreamReader(body.inputStream, 'UTF-8')
        def json = null
        try {
            json = new JsonSlurper().parse(reader)
        } catch (groovy.json.JsonException ex) {
            message = "Problem parsing JSON: $ex.message"
            status = 400
            return
        } finally {
            if (reader != null) {
                reader.close();
            }
        }

        log.info "Update configuration with parameters: " + params
        def action = params['action'] ? params['action'][0] as String : "add"

        log.info "Json content: " + json

        if (action == 'reset'){
            log.info "Reseting configuration before adds"
            config.repositories.clear()
        }

        config.repositories << json.repositories
    }
}

download {
    beforeDownloadRequest { Request request, RepoPath repoPath ->
        config.repositories.each{ repoName, repoConfig ->
            if (repoName == repoPath.repoKey) {
                repoConfig.patterns.each { pattern ->
                    PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
                    if (isRemote(repoPath.repoKey) && isGeneric(repoPath.repoKey) && shouldExpire(repoPath, repoConfig.delay)) {
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
}

def configFile = new File(ctx.artifactoryHome.etcDir, CONFIG_FILE_PATH)

if (configFile.exists()) {
    config = new JsonSlurper().parse(configFile.toURL())
    log.info "Check 'Expire Files Metadata' plugin repositories list: $config.repositories"
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
