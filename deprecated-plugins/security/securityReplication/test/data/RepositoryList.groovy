package data

class RepositoryList {

	RepositoryMaven mymavenrepos = new RepositoryMaven ();
	RepositoryGradle mygradlerepos = new RepositoryGradle ();
	RepositoryNPM mynpmrepos = new RepositoryNPM ();
	RepositoryDocker mydockerrepos = new RepositoryDocker ();
	RepositoryIvy myivyrepos = new RepositoryIvy ();
	RepositorySBT mysbtrepos = new RepositorySBT ();
	RepositoryDebian mydebianrepos = new RepositoryDebian ();
	RepositoryNuget mynugetrepos = new RepositoryNuget ();
	RepositoryGems mygemsrepos = new RepositoryGems ();
	RepositoryPython mypythonrepos = new RepositoryPython ();
	RepositoryYum myyumrepos = new RepositoryYum ();
	RepositoryGeneric mygenericrepos = new RepositoryGeneric ();
	RepositoryVagrant myvagrantrepos = new RepositoryVagrant ();
	RepositoryBower mybowerrepos = new RepositoryBower ();
	RepositoryGitLfs mygitlfsrepos = new RepositoryGitLfs ();
	RepositoryCocoaPod mycocoapodrepos = new RepositoryCocoaPod ();
	RepositoryOpkg myopkgrepos = new RepositoryOpkg ();
	RepositoryConan myconanrepos = new RepositoryConan();
	
	def devOpsRepoList = [];
	def devRepoList = [];
	def qaRepoList = [];

	def getLocalRepoList (){
		
		return ([mymavenrepos.myMavenReposList, mygradlerepos.myGradleReposList, mynpmrepos.myNPMReposList,
			mydockerrepos.myDockerReposList, myivyrepos.myivyReposList, mysbtrepos.mysbtReposList, mydebianrepos.mydebianReposList,
			mynugetrepos.myNugetReposList, mygemsrepos.myGemsReposList,  mypythonrepos.mypypiReposList, myyumrepos.myyumReposList,
			mygenericrepos.mygenericReposList, myvagrantrepos.myvagrantReposList, mybowerrepos.mybowerReposList,
			mygitlfsrepos.mygitlfsReposList, mycocoapodrepos.mycocoapodReposList, myopkgrepos.myopkgReposList, myconanrepos.myconanReposList]);
	}
	
	def getRemoteRepoList () {
		
		return ([mymavenrepos.myMavenRemoteList, mynpmrepos.myNPMRemoteList, mydockerrepos.myDockerRemoteList, mynugetrepos.myNugetRemoteList, mygemsrepos.myGemsRemoteList,
			mypythonrepos.mypypiRemoteList, mybowerrepos.mybowerRemoteList, mycocoapodrepos.mycocoapodRemoteList]); 
	}
	
	def getVirtualRepoList () {
		
		return ([mymavenrepos.myMavenVirtualList, mygradlerepos.myGradleVirtualList, mynpmrepos.myNPMVirtualList, mydockerrepos.myDockerVirtualList, 
			myivyrepos.myivyVirtualList, mysbtrepos.mysbtVirtualList, mynugetrepos.myNugetVirtualList, mygemsrepos.myGemsVirtualList, 
			mypythonrepos.mypypiVirtualList, mygenericrepos.mygenericVirtualList, mybowerrepos.mybowerVirtualList]); 
	}
	
	private void createDevOpsRepoList () {
		
		devOpsRepoList.addAll ( mymavenrepos.myMavenDevOpsPermissionList + mygradlerepos.myGradleDevOpsPermissionList +
			mynpmrepos.myNPMDevOpsPermissionList + mydockerrepos.myDockerDevOpsPermissionList + myivyrepos.myivyDevOpsPermissionList +
			mysbtrepos.mysbtDevOpsPermissionList + mydebianrepos.mydebianDevOpsPermissionList + mynugetrepos.myNugetDevOpsPermissionList +
			mygemsrepos.myGemsDevOpsPermissionList + mypythonrepos.mypypiDevOpsPermissionList + myyumrepos.myyumDevOpsPermissionList +
			mygenericrepos.mygenericDevOpsPermissionList + myvagrantrepos.myvagrantDevOpsPermissionList + mybowerrepos.mybowerDevOpsPermissionList +
			mygitlfsrepos.mygitlfsDevOpsPermissionList + mycocoapodrepos.mycocoapodDevOpsPermissionList + myopkgrepos.myopkgDevOpsPermissionList + myconanrepos.myconanDevOpsPermissionList);
	}
	
	private void createDevRepoList () {
		
		devRepoList.addAll (mymavenrepos.myMavenDevPermissionList + mygradlerepos.myGradleDevPermissionList +
			mynpmrepos.myNPMDevPermissionList + mydockerrepos.myDockerDevPermissionList + myivyrepos.myivyDevPermissionList +
			mysbtrepos.mysbtDevPermissionList + mydebianrepos.mydebianDevPermissionList + mynugetrepos.myNugetDevPermissionList +
			mygemsrepos.myGemsDevPermissionList + mypythonrepos.mypypiDevPermissionList + myyumrepos.myyumDevPermissionList +
			mygenericrepos.mygenericDevPermissionList + myvagrantrepos.myvagrantDevPermissionList + mybowerrepos.mybowerDevPermissionList +
			mygitlfsrepos.mygitlfsDevPermissionList + mycocoapodrepos.mycocoapodDevPermissionList + myopkgrepos.myopkgDevPermissionList + myconanrepos.myconanDevPermissionList);
	}
	
	private void createqaRepoList () {
		
		qaRepoList.addAll (mymavenrepos.myMavenQAPermissionList + mygradlerepos.myGradleQAPermissionList +
			mynpmrepos.myNPMQAPermissionList + mydockerrepos.myDockerQAPermissionList + myivyrepos.myivyQAPermissionList +
			mysbtrepos.mysbtQAPermissionList + mydebianrepos.mydebianQAPermissionList + mynugetrepos.myNugetQAPermissionList +
			mygemsrepos.myGemsQAPermissionList + mypythonrepos.mypypiQAPermissionList + myyumrepos.myyumQAPermissionList +
			mygenericrepos.mygenericQAPermissionList + myvagrantrepos.myvagrantQAPermissionList + mybowerrepos.mybowerQAPermissionList +
			mygitlfsrepos.mygitlfsQAPermissionList + mycocoapodrepos.mycocoapodQAPermissionList + myopkgrepos.myopkgQAPermissionList + myconanrepos.myconanQAPermissionList);
	}
	
	public void createPermissionTargets () {
		createDevOpsRepoList ();
		createDevRepoList ();
		createqaRepoList ();
	}
	
}
