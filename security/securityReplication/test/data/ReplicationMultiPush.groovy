package data

import definitions.MultiPushList
import definitions.MultiPushRepo
import definitions.Constants

class ReplicationMultiPush {
	def repokey; 
	def myMultiPushRepos = [];
	
	ReplicationMultiPush (def repokey) {
		this.repokey = repokey; 
	}
	
	public void addMultiPushRepo (def uri, def username, def password) {
		def config = new Constants ();
		def targeturl = "http://" + uri + this.repokey + "/"; 
		
		 myMultiPushRepos << new MultiPushRepo (url: targeturl, repoKey: this.repokey,
			 username: username, password: password)
	} 
	
	public MultiPushList createMultiPushRepoList () {
		MultiPushList multipushlist = new MultiPushList (replications: myMultiPushRepos)
		return multipushlist; 
	}
}
