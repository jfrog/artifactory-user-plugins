import org.artifactory.repo.RepositoryConfiguration
import org.artifactory.repo.Repositories
import org.artifactory.repo.RepoPath
import org.artifactory.fs.FileLayoutInfo;

jobs {
    // Set Repositories as needed here
    def searchRepoNames = ['pypi-local','maven-local','pypi-local2']
    def missingProperty = "pypi.normalized.name"
    def nameProperty = "pypi.name"

    // Interval can be set at user's discretion. Default is every 30 seconds.
    addPypiNormalizedName(interval: 30000, delay: 100) {
        log.info "Searching for inputted repositories..."
        for(item in searchRepoNames) {
            if(repositories.localRepositories.contains(item)) {
                log.info "$item exists as a local repository"
                def testRepo = repositories.getRepositoryConfiguration(item)
                //Check if it is a Pypi Repo
                if(!testRepo.isEnablePypiSupport()) {
                    log.info "$item is not a Pypi Repostory"
                    continue
                }
                def filePattern = '*'
                List<RepoPath> repoPath = searches.artifactsByName(filePattern, item)
                if(repoPath != null && repoPath.size() > 0 ) {
                    log.info "looking for artifacts..."
                    for(artifacts in repoPath) {
                        if(!repositories.hasProperty(artifacts, missingProperty) && repositories.hasProperty(artifacts, nameProperty)) {
                            def namePropertyValue = repositories.getProperty(artifacts, nameProperty)
                            //set pypi.normalized.name correctly
                            def missingName = namePropertyValue.toLowerCase().replaceAll("_","-")
                            log.info "$artifacts does not have required property, adding $missingProperty as $missingName"
                            repositories.setProperty(artifacts, missingProperty, missingName)
                        } else {
                            if(repositories.hasProperty(artifacts, missingProperty)) log.info "$artifacts already has $missingProperty property"
                            else {
                                log.info "$artifacts does not have $nameProperty property"
                            }
                        }
                    }
                } else {
                    log.info "$item does not contain any valid artifacts"
                    continue
                }
            } else {
                log.info "$item does not exist or is not a valid repository"
            }
        }
    }
}
