import groovy.json.JsonBuilder
import org.artifactory.repo.RepoPathFactory
import org.artifactory.exception.CancelException

storage {
  
 /**
 * Handle before create events.
 *
 * Closure parameters:
 * item (org.artifactory.fs.ItemInfo) - the original item being created.
 */
 beforeCreate { item ->
   asSystem {
     def repoPath = item.getRepoPath()
     def itemRepoPathStr = repoPath.toPath()
     log.debug("Before create of repo path ${itemRepoPathStr}") 
     while (!repoPath.isRoot()) {
       repoPath = repoPath.getParent()
       def repoPathStr = repoPath.toPath()
       log.debug("Checking exists repo path ${repoPathStr}") 
       if (repositories.exists(repoPath)) {
         def properties = repositories.getProperties(repoPath)
         log.debug("Checking for property repository.path.quota for repo path ${repoPathStr}") 
         if (properties.containsKey("repository.path.quota")) {
           log.debug("Checking quota condition for repo path ${repoPathStr}") 
           def quotaInBytesStr = properties.getFirst("repository.path.quota")
           log.debug("Quota is ${quotaInBytesStr} for repo path ${repoPathStr}") 
           def quotaInBytes = quotaInBytesStr.isLong() ? (quotaInBytesStr as long) : null
           if (null == quotaInBytes) {
            log.warning("Repository path quota of ${quotaInBytesStr} for ${repoPathStr} is invalid")
           }
           def currentSizeInBytes = repositories.getArtifactsSize(repoPath)
           if (currentSizeInBytes >= quotaInBytes) {
             log.error("Repository path quota of ${quotaInBytesStr} exceeded for ${repoPathStr}")
             throw new CancelException("Repository path storage quota exceeded.", 413)
           }
         }
       }
     }
   }
 }
/**
 * Handle before property create events.
 *
 * Closure parameters:
 * item (org.artifactory.fs.ItemInfo) - the item on which the property is being set.
 * name (java.lang.String) - the name of the property being set.
 * values (java.lang.String[]) - A string array of values being assigned to the property.
 */
 beforePropertyCreate { item, name, values ->
   if (name == "repository.path.quota" && !security.isAdmin()) {
     throw new CancelException("Not authorized to create this property.", 401)
   }
 }
/**
 * Handle before property delete events.
 *
 * Closure parameters:
 * item (org.artifactory.fs.ItemInfo) - the item from which the property is being deleted.
 * name (java.lang.String) - the name of the property being deleted.
 */
 beforePropertyDelete { item, name ->
   if (name == "repository.path.quota" && !security.isAdmin()) {
     throw new CancelException("Not authorized to delete this property.", 401)
   }
 }
} 

