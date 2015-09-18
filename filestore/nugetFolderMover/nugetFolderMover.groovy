/*
 * Copyright (C) 2015 JFrog Ltd.
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

import org.artifactory.common.StatusHolder
import org.artifactory.fs.FileLayoutInfo
import org.artifactory.fs.ItemInfo
import org.artifactory.repo.RepoPath
import org.artifactory.repo.RepoPathFactory

download {
    jobs {
        nugetMover(cron: "0 0/5 * * * ?") {
            List<String> localRepoKeys = getLocalNugetRepositories()
            log.debug "Found ${localRepoKeys.size()} local Nuget repositories"
            localRepoKeys.each { String repoKey ->
                log.debug "Looking for Nuget packages in the root of repo $repoKey"
                RepoPath repoRoot = RepoPathFactory.create(repoKey, '/')
                List<ItemInfo> children = repositories.getChildren(repoRoot).findAll { ItemInfo itemInfo ->
                    !itemInfo.isFolder()
                }
                log.debug "Found ${children.size()} candidates"
                children.each { ItemInfo itemInfo ->
                    org.artifactory.md.Properties properties = repositories.getProperties(itemInfo.repoPath)
                    if (properties.containsKey('nuget.id') && properties.containsKey('nuget.version')) {
                        String id = properties.getFirst('nuget.id')
                        String version = properties.getFirst('nuget.version')
                        if (id && version) {
                            log.debug "Found a new Nuget package '${itemInfo.repoPath.toPath()}'"
                            FileLayoutInfo layout = new NugetLayoutInfo(id, version)
                            RepoPath newPath
                            try {
                                newPath = repositories.getArtifactRepoPath(layout, repoRoot.repoKey)
                            } catch (Exception e) {
                                log.error 'Failed to calculate Nuget artifact path using repository layout', e
                                return
                            }
                            StatusHolder holder = repositories.move(itemInfo.repoPath, newPath)
                            if (holder.error) {
                                log.error "Failed to move Nuget artifact from '${itemInfo.repoPath.toPath()}' to '${newPath.toPath()}'"
                            } else {
                                log.debug "Moved '${itemInfo.name}' from '${itemInfo.repoPath.toPath()}' to '${newPath.toPath()}'"
                            }
                        }
                    }
                }
            }
        }
    }
}

private List<String> getLocalNugetRepositories() {
    List<String> localRepoKeys = repositories.getLocalRepositories()
    localRepoKeys.findAll { String repoKey ->
        repositories.getRepositoryConfiguration(repoKey)?.isEnableNuGetSupport()
    }
}

class NugetLayoutInfo implements FileLayoutInfo {
    String organization
    String module
    String baseRevision
    String folderIntegrationRevision
    String fileIntegrationRevision
    String classifier
    String ext
    String type
    Map<String, String> customFields = [:]

    NugetLayoutInfo(String id, String version) {
        module = id
        organization = id
        baseRevision = version
        ext = 'nupkg'
    }

    @Override
    String getCustomField(String key) {
        customFields[key]
    }

    @Override
    boolean isValid() {
        organization && module && baseRevision
    }

    @Override
    String getPrettyModuleId() {
        module
    }

    @Override
    boolean isIntegration() {
        false
    }
}
