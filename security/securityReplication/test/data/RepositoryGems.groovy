package data

import definitions.RepositoryLocalClass
import definitions.RepositoryRemoteClass
import definitions.RepositoryVirtualClass

class RepositoryGems {

		def myGemsDev = new RepositoryLocalClass (
		key: 'gems-dev-local',
		packageType: 'gems',
		description: "gems-development-path",
		repoLayoutRef: 'simple-default',
		handleReleases: 'false',
		handleSnapshots: 'true')

		def myGemsRelease = new RepositoryLocalClass (
		key: 'gems-release-local',
		packageType: 'gems',
		description: "development-to-release-path",
		repoLayoutRef: 'simple-default',
		handleReleases: 'true',
		handleSnapshots: 'false')

		def myGemsRemote = new RepositoryRemoteClass (
		key: 'gems-remote',
		packageType: 'gems',
		url: 'https://rubygems.org/',
		description: 'Gems-remote-repository',
		handleReleases: 'true',
		handleSnapshots: 'true')

		def myGemsReleaseVirtual = new RepositoryVirtualClass (
		key: 'gems-release-virtual',
		packageType: 'gems',
		repositories:['gems-release-local','gems-remote'],
		description: 'demo-release-virtual-repository',
		defaultDeploymentRepo : 'gems-release-local'
		)

		def myGemsDevVirtual = new RepositoryVirtualClass (
		key: 'gems-dev-virtual',
		packageType: 'gems',
		repositories: ['gems-dev-local','gems-remote'],
		description: 'demo-snapshot-virtual-repository',
		defaultDeploymentRepo : 'gems-dev-local'
		)

		def myGemsReposList = [myGemsDev, myGemsRelease]
		def myGemsVirtualList = [myGemsReleaseVirtual, myGemsDevVirtual]
		def myGemsRemoteList = [myGemsRemote]
		def myGemsDevOpsPermissionList = [myGemsDev['key'], myGemsRelease['key']]
		def myGemsDevPermissionList = [myGemsDev['key']]
		def myGemsQAPermissionList = [myGemsDev['key'], myGemsRelease['key']]
	
}
