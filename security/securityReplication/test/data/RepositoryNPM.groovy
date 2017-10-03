package data

import definitions.RepositoryLocalClass
import definitions.RepositoryRemoteClass
import definitions.RepositoryVirtualClass

class RepositoryNPM {
	
	def myNPMDev = new RepositoryLocalClass (
		key: 'npm-dev-local',
		packageType: 'npm',
		description: "demo-npm-development-path",
		repoLayoutRef: 'npm-default',
		handleReleases: 'false',
		handleSnapshots: 'true')
	
	def myNPMRelease = new RepositoryLocalClass (
		key: 'npm-release-local',
		packageType: 'npm',
		description: "demo-promotion-to-release-path",
		repoLayoutRef: 'npm-default',
		handleReleases: 'true',
		handleSnapshots: 'false')
	
	def myNPMRemote = new RepositoryRemoteClass (
		key: 'npm-remote',
		packageType: 'npm',
		url: 'https://registry.npmjs.org',
		description: 'NPM-remote-repository',
		handleReleases: 'true',
		handleSnapshots: 'true')
	
	def myNPMReleaseVirtual = new RepositoryVirtualClass (
		key: 'npm-release-virtual',
		packageType: 'npm',
		repositories:['npm-release-local', 'npm-remote'],
		description: 'demo-release-virtual-repository',
		defaultDeploymentRepo : 'npm-release-local'
		)
	
	def myNPMDevVirtual = new RepositoryVirtualClass (
		key: 'npm-dev-virtual',
		packageType: 'npm',
		repositories: ['npm-dev-local','npm-remote'],
		description: 'demo-snapshot-virtual-repository',
		defaultDeploymentRepo : 'npm-dev-local'
		)
	
	def myNPMReposList = [myNPMDev, myNPMRelease]
	def myNPMVirtualList = [myNPMReleaseVirtual, myNPMDevVirtual]
	def myNPMRemoteList = [myNPMRemote]
	def myNPMDevOpsPermissionList = [myNPMDev['key'], myNPMRelease['key']]
	def myNPMDevPermissionList = [myNPMDev['key']]
	def myNPMQAPermissionList = [myNPMDev['key'], myNPMRelease['key']]
}
