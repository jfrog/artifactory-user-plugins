package data

import definitions.RepositoryLocalClass
import definitions.RepositoryRemoteClass
import definitions.RepositoryVirtualClass

class RepositoryBower {
	def mybowerDev = new RepositoryLocalClass (
		key: 'bower-dev-local',
		packageType: 'bower',
		description: "bower-development-path",
		repoLayoutRef: 'bower-default',
		handleReleases: 'false',
		handleSnapshots: 'true')
	
	def mybowerRelease = new RepositoryLocalClass (
		key: 'bower-release-local',
		packageType: 'bower',
		description: "development-to-release-path",
		repoLayoutRef: 'bower-default',
		handleReleases: 'true',
		handleSnapshots: 'false')
	
	def mybowerRemote = new RepositoryRemoteClass (
		key: 'bower-remote',
		packageType: 'bower',
		url: 'https://github.com/',
		description: 'bower-remote-repository',
		handleReleases: 'true',
		handleSnapshots: 'true')
	
	def mybowerReleaseVirtual = new RepositoryVirtualClass (
		key: 'bower-release-virtual',
		packageType: 'bower',
		repositories:['bower-release-local','bower-remote'],
		description: 'demo-release-virtual-repository',
		defaultDeploymentRepo : 'bower-release-local'
		)
	
	def mybowerDevVirtual = new RepositoryVirtualClass (
		key: 'bower-dev-virtual',
		packageType: 'bower',
		repositories: ['bower-dev-local','bower-remote'],
		description: 'demo-snapshot-virtual-repository',
		defaultDeploymentRepo : 'bower-dev-local'
		)
	
	def mybowerReposList = [mybowerDev, mybowerRelease]
	def mybowerVirtualList = [mybowerReleaseVirtual, mybowerDevVirtual]
	def mybowerRemoteList = [mybowerRemote]
	def mybowerDevOpsPermissionList = [mybowerDev['key'], mybowerRelease['key']]
	def mybowerDevPermissionList = [mybowerDev['key']]
	def mybowerQAPermissionList = [mybowerDev['key'], mybowerRelease['key']]
}
