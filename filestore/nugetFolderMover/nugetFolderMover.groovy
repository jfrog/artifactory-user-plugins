import org.artifactory.common.StatusHolder
import org.artifactory.fs.FileLayoutInfo
import org.artifactory.repo.RepoPath
import org.artifactory.repo.RepoPathFactory
import org.artifactory.fs.ItemInfo
import org.artifactory.resource.ResourceStreamHandle
import org.jfrog.metadata.extractor.GemFileInfoExtractor
import org.jfrog.metadata.extractor.api.GemArtifact
import org.jfrog.metadata.extractor.model.GemInfo

download {
    jobs {

        /**
         * A job definition.
         * The first value is a unique name for the job.
         * Job runs are controlled by the provided interval or cron expression, which are mutually exclusive.
         * The actual code to run as part of the job should be part of the job's closure.
         *
         * Parameters:
         * delay (long) - An initial delay in milliseconds before the job starts running (not applicable for a cron job).
         * interval (long) -  An interval in milliseconds between job runs.
         * cron (java.lang.String) - A valid cron expression used to schedule job runs (see: http://www.quartz-scheduler.org/docs/tutorial/TutorialLesson06.html)
         */
         
       

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
                            FileLayoutInfo layout = new NugetLayoutInfo(id,  version)
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
