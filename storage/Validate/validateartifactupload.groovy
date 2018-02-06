import org.artifactory.build.promotion.PromotionConfig
import org.artifactory.build.staging.ModuleVersion
import org.artifactory.build.staging.VcsConfig
import org.artifactory.exception.CancelException
import org.artifactory.repo.RepoPathFactory
import org.artifactory.request.Request
import org.artifactory.util.StringInputStream

storage {
      beforeCreate { item ->
        def ff = item.isFolder() ? 'folder' : 'file'        
        def repositorykey = item.repoPath.repoKey
        def repositoryname ='repositoryName'

if (repositorykey==repositoryname)
{
        
           	 def a = item.name
			 def b = a.toLowerCase()	
				if(a.equals(b))
				{
					log.info("all are lowercase and import is success")
				}else{				
				 log.info("Please upload Artifacts with lowercase")
				 throw new CancelException("Please upload Artifacts with lowercase", 403)
                }
	      }   
}
}
