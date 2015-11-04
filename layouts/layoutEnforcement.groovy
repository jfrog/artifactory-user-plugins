import org.artifactory.exception.CancelException

storage {

	beforeCreate { item ->
		if (!item.isFolder()){
			// Get the layout information of the item 
			def layoutInfo = repositories.getLayoutInfo(item.repoPath)
			String groupId = layoutInfo.getOrganization() 
			String artifactId = layoutInfo.getModule() 
			String versionId = layoutInfo.getBaseRevision()
			// If the item doesn't contain the Maven Layout structure, reject the upload 
			if ( "${groupId}" == "null" || "${artifactId}" == "null" || "${versionId}" == "null")
			{ 
				status = 403
				message = 'This artifact did not match the layout.'
				log.warn "This artifact did not match the layout"
				throw new CancelException("This artifact did not match the layout")
			}

		} 
	}
}
