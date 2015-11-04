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
				throw new CancelException("Artifact create not permitted without Maven Layout", 403) 
			}

		} 
	}
}
