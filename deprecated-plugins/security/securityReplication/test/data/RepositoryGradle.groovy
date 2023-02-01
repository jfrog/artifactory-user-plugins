package data

import definitions.RepositoryLocalClass
import definitions.RepositoryVirtualClass

class RepositoryGradle {
	
	def myGradleDev = new RepositoryLocalClass (
		key: 'gradle-dev-local',
		packageType: 'gradle',
		description: 'demo-gradle-development-path',
		repoLayoutRef: 'gradle-default',
		handleReleases: 'false',
		handleSnapshots: 'true')
		
	def myGradleRelease = new RepositoryLocalClass (
		key: 'gradle-release-local',
		packageType: 'gradle',
		description: 'demo-promotion-to-release-path',
		repoLayoutRef: 'gradle-default',
		handleReleases: 'true',
		handleSnapshots: 'false')
		
	def myGradleReleaseVirtual = new RepositoryVirtualClass (
		key: 'gradle-release-virtual',
		packageType: 'gradle',
		repositories:['gradle-release-local','jcenter'],
		description: 'demo-release-virtual-repository',
		defaultDeploymentRepo : 'gradle-release-local'
	)
		
	def myGradleDevVirtual = new RepositoryVirtualClass (
		key: 'gradle-dev-virtual',
		packageType: 'gradle',
		repositories: ['gradle-dev-local','jcenter'],
		description: 'demo-snapshot-virtual-repository',
		defaultDeploymentRepo : 'gradle-dev-local'
	)
		
	def myGradleReposList = [myGradleDev, myGradleRelease]
	def myGradleVirtualList = [myGradleReleaseVirtual, myGradleDevVirtual]
	def myGradleDevOpsPermissionList = [myGradleDev['key'], myGradleRelease['key']]
	def myGradleDevPermissionList = [myGradleDev['key']]
	def myGradleQAPermissionList = [myGradleDev['key'], myGradleRelease['key']]
}
	
