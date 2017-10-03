package data

import definitions.PermissionClass
import org.jfrog.artifactory.client.model.Privilege

class PermissionTargets {

	RepositoryList repositoryList = new RepositoryList()

	PermissionTargets() {
		repositoryList.createPermissionTargets();
	}
	
	PermissionClass permissionDevOps = new PermissionClass (
		name: 'devops-team',
		repositories: repositoryList.devOpsRepoList,
		users: [devops1:[Privilege.DELETE, Privilege.ANNOTATE,Privilege.DEPLOY, Privilege.READ], devops2:[Privilege.DEPLOY, Privilege.ANNOTATE, Privilege.DELETE, Privilege.READ]],
		groups: ["devops-group":[Privilege.DELETE, Privilege.ANNOTATE,Privilege.DEPLOY, Privilege.READ]]
	)

	PermissionClass permissionQA = new PermissionClass (
		name: 'qa-team',
		repositories: repositoryList.qaRepoList,
		users: [qa1:[Privilege.DELETE, Privilege.ANNOTATE,Privilege.DEPLOY, Privilege.READ], qa2:[Privilege.DELETE, Privilege.ANNOTATE,Privilege.DEPLOY, Privilege.ANNOTATE]],
		groups: ["qa-group":[Privilege.DELETE, Privilege.ANNOTATE,Privilege.DEPLOY, Privilege.READ]]
	)

	PermissionClass permissionDev = new PermissionClass (
		name: 'development-team',
		repositories: repositoryList.devRepoList,
		users: [dev1:[Privilege.DELETE, Privilege.ANNOTATE,Privilege.DEPLOY, Privilege.READ], dev2:[Privilege.DELETE, Privilege.ANNOTATE,Privilege.DEPLOY, Privilege.READ]],
		groups:["dev-group":[Privilege.DELETE, Privilege.ANNOTATE,Privilege.DEPLOY, Privilege.READ]]
	)

	PermissionClass permissionSolDev = new PermissionClass (
			name: 'soldev-team',
			repositories: repositoryList.devRepoList,
			users: [stanleyf:[Privilege.DELETE, Privilege.ANNOTATE,Privilege.DEPLOY, Privilege.READ], edmilisonp:[Privilege.DELETE, Privilege.ANNOTATE,Privilege.DEPLOY, Privilege.READ],
					jainishs:[Privilege.DELETE, Privilege.ANNOTATE,Privilege.DEPLOY, Privilege.READ],travisf:[Privilege.DELETE, Privilege.ANNOTATE,Privilege.DEPLOY, Privilege.READ],
					shikharr:[Privilege.DELETE, Privilege.ANNOTATE,Privilege.DEPLOY, Privilege.READ], ankushc:[Privilege.DELETE, Privilege.ANNOTATE,Privilege.DEPLOY, Privilege.READ],
					eliom:[Privilege.DELETE, Privilege.ANNOTATE,Privilege.DEPLOY, Privilege.READ]],
			groups:["dev-group":[Privilege.DELETE, Privilege.ANNOTATE,Privilege.DEPLOY, Privilege.READ], "jfrog":[Privilege.ANNOTATE, Privilege.READ]]
	)

	def permissionList = [permissionDevOps, permissionDev, permissionQA, permissionSolDev]
}
