import org.artifactory.repo.RepoPath
import org.artifactory.search.aql.AqlResult
import org.artifactory.repo.RepoPathFactory



// Example REST API calls:
// curl -X POST -v -u admin:password "http://localhost:8081/artifactory/api/plugins/execute/deleteByPropertyValue?params=propertyName=test;propertyValue=2;repoGlobal=virtual-libs-release-local"


// with this we made a interval for delete files with the properti name junked and value less than 2

String intervalPropertiName = "borramos"
int intervalPropertieValue = 2
String intervalRepo = "politicas-helm"
Boolean intervalDryRun = true
// To define the time interval over which this plugin will run
int intervalExecution = 160000 // in milisecond 
int delayExecution = 100 // in milisecond
// the intervan definition
jobs {
  deleteByPropertyValueInterval_1(interval: intervalExecution, delay: delayExecution) {
      repo = new String("")
      log.info "Executamos o intervalo de borrado "

      fileCleanup(intervalPropertiName, intervalPropertieValue, intervalRepo, intervalDryRun)   
  }
}

executions {
    deleteByPropertyValue() { params ->
        propertyName = params?.get('propertyName')?.get(0) as String
        propertyValue = params?.get('propertyValue')?.get(0) as int
        repoGlobal = params?.get('repoGlobal')?.get(0) as String
        dryRun = new Boolean(params?.get('dryRun')?.get(0))
        repo = new String("")
        fileCleanup(propertyName, propertyValue, repoGlobal, dryRun)
        
    }
}


private def fileCleanup(propertyName, propertyValue, repoGlobal, dryRun) {
    log.info "Looking for files with property of $propertyName with a value lower than $propertyValue... in $repo"
    def pathsTmp = []
    def reposToDelete = []
    def count = 0
    def aqlRepo = "items.find({\"repo\":\"" + repoGlobal + "\",\"property.key\":{\"\$eq\":\"" + propertyName + "\"}})"
    // modified by Ruben From here
    // with this option we can use virtual group for manage
    searches.aql(aqlRepo.toString()) {
        for (item in it) {
            reposToDelete.add(item.repo)
        }
    }
    // with this for we can search in repos.
    for (repo in reposToDelete) {
    def aql = "items.find({\"repo\":\"" + repo + "\",\"property.key\":{\"\$eq\":\"" + propertyName + "\"}})"
    searches.aql(aql) { AqlResult result ->
        result.each { Map item ->
            String itemPath = item.path + "/" + item.name
            log.info "Found: $itemPath in repo $repo"
            RepoPath repoPath = RepoPathFactory.create(repo, itemPath)

            def keyValue = repositories.getProperty(repoPath, propertyName)
            log.info "$propertyName: $keyValue"
            if (keyValue.toInteger() < propertyValue) {
                log.info "Deleting $repoPath (dryRun: $dryRun) in repo $repo"
                if (!dryRun) {
                    repositories.delete repoPath
                }
                count++
            }
        }
    }
    }
    if (count > 0){
        log.info ("Succesfully deleted  $count files (dryRun: $dryRun)")
    } else
        log.info ("No files with property: '$propertyName' and property value less than '$propertyValue' found. Did not delete anything")
    status = 200
}
