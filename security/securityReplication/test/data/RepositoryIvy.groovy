package data

import definitions.RepositoryLocalClass
import definitions.RepositoryVirtualClass

class RepositoryIvy {
	def myivyDev = new RepositoryLocalClass (
		key: 'ivy-dev-local',
		packageType: 'ivy',
		description: 'demo-ivy-development-path',
		repoLayoutRef: 'ivy-default',
		handleReleases: 'false',
		handleSnapshots: 'true')
	
	def myivyRelease = new RepositoryLocalClass (
		key: 'ivy-release-local',
		packageType: 'ivy',
		description: 'demo-promotion-to-release-path',
		repoLayoutRef: 'ivy-default',
		handleReleases: 'true',
		handleSnapshots: 'false')
	
	def myivyReleaseVirtual = new RepositoryVirtualClass (
		key: 'ivy-release-virtual',
		packageType: 'ivy',
		repositories:['ivy-release-local','jcenter'],
		description: 'demo-release-virtual-repository',
		defaultDeploymentRepo : 'ivy-release-local'
		)
	
	def myivyDevVirtual = new RepositoryVirtualClass (
		key: 'ivy-dev-virtual',
		packageType: 'ivy',
		repositories: ['ivy-dev-local','jcenter'],
		description: 'demo-snapshot-virtual-repository',
		defaultDeploymentRepo : 'ivy-dev-local'
		)
	
	def myivyReposList = [myivyDev, myivyRelease]
	def myivyVirtualList = [myivyReleaseVirtual, myivyDevVirtual]
	def myivyDevOpsPermissionList = [myivyRelease['key']]
	def myivyDevPermissionList = [myivyDev['key']]
	def myivyQAPermissionList = [myivyDev['key'], myivyRelease['key']]
}
