import org.artifactory.repo.RepositoryConfiguration
import org.artifactory.repo.Repositories
import org.artifactory.repo.RepoPath
import org.artifactory.fs.FileLayoutInfo;

jobs {
    //Interval is temporary, can be set at user's discretion. Default is every 30 seconds.
    //Set Repositories as needed here, example listed below
    //def searchRepoNames = ['pypi-local','empty-test','maven-local','pypi-local2']
    def searchRepoNames = []
    List<String> repoNames = repositories.getLocalRepositories()
    def missingProperty = "pypi.normalized.name"
    def nameProperty = "pypi.name"

    addProperty(interval: 30000, delay: 100) {
        log.info "$repoNames list of available repos"
        log.info "Searching for inputted repositories..."

        for(item in searchRepoNames) {
            if(repoNames.contains(item)) {
                log.info "$item exists as a local repository"
                def testRepo = repositories.getRepositoryConfiguration(item) 
                if(testRepo == null || item == null) {
			repoNames.remove(item)
			log.info "$item no longer exists, removing from repository list"
			continue
		}
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
                } 
                else { 
                    log.info "$item does not contain any valid artifacts"
                    continue
                }                
            }
            else { 
                log.info "$item does not exist or is not a valid repository"
            }
        }
    }
}
