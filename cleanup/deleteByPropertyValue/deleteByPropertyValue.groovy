import org.artifactory.repo.RepoPath
import org.artifactory.search.aql.AqlResult
import org.artifactory.repo.RepoPathFactory


// Example REST API calls:
// curl -X POST -v -u admin:password "http://localhost:8081/artifactory/api/plugins/execute/deleteByPropertyValue?params=propertyName=test;propertyValue=2;repo=libs-release-local"




executions {
    deleteByPropertyValue() { params ->
        propertyName = params?.get('propertyName')?.get(0) as String
        propertyValue = params?.get('propertyValue')?.get(0) as int
        repo = params?.get('repo')?.get(0) as String
        dryRun = new Boolean(params?.get('dryRun')?.get(0))
        fileCleanup(propertyName, propertyValue, repo, dryRun)
    }
}



private def fileCleanup(propertyName, propertyValue, repo, dryRun) {
    log.info "Looking for files with property of $propertyName with a value lower than $propertyValue... in $repo"

    def count = 0
    def aql = "items.find({\"repo\":\"" + repo + "\",\"property.key\":{\"\$eq\":\"" + propertyName + "\"}})"

    searches.aql(aql) { AqlResult result ->
        result.each { Map item ->
            String itemPath = item.path + "/" + item.name
            log.info "Found: $itemPath"
            RepoPath repoPath = RepoPathFactory.create(repo, itemPath)

            def keyValue = repositories.getProperty(repoPath, propertyName)
            log.info "$propertyName: $keyValue"
            if (keyValue.toInteger() < propertyValue) {
                log.info "Deleting $repoPath (dryRun: $dryRun)"
                if (!dryRun) {
                    repositories.delete repoPath
                }
                count++
            }
        }
    }
    if (count > 0){
        log.info ("Succesfully deleted  $count files (dryRun: $dryRun)")
    } else
        log.info ("No files with property: '$propertyName' and property value less than '$propertyValue' found. Did not delete anything")
    status = 200
}
