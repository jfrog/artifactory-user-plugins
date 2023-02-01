package data

import definitions.RepositoryLocalClass
import definitions.RepositoryRemoteClass
import definitions.RepositoryVirtualClass

class RepositoryNuget {
		
		def myNugetDev = new RepositoryLocalClass (
			key: 'nuget-dev-local',
			packageType: 'nuget',
			description: "demo-Nuget-development-path",
			repoLayoutRef: 'nuget-default',
			handleReleases: 'false',
			handleSnapshots: 'true')
		
		def myNugetRelease = new RepositoryLocalClass (
			key: 'nuget-release-local',
			packageType: 'nuget',
			description: "demo-promotion-to-release-path",
			repoLayoutRef: 'nuget-default',
			handleReleases: 'true',
			handleSnapshots: 'false')
		
		def myNugetRemote = new RepositoryRemoteClass (
			key: 'nuget-remote',
			packageType: 'nuget',
			url: 'https://www.nuget.org/',
			description: 'nuget-remote-repository',
			handleReleases: 'true',
			handleSnapshots: 'true')
		
		def myNugetReleaseVirtual = new RepositoryVirtualClass (
			key: 'nuget-release-virtual',
			packageType: 'nuget',
			repositories:['nuget-release-local','nuget-remote'],
			description: 'demo-release-virtual-repository',
			defaultDeploymentRepo : 'nuget-release-local'
			)
		
		def myNugetDevVirtual = new RepositoryVirtualClass (
			key: 'nuget-dev-virtual',
			packageType: 'nuget',
			repositories: ['nuget-dev-local','nuget-remote'],
			description: 'demo-snapshot-virtual-repository',
			defaultDeploymentRepo : 'nuget-dev-local'
			)
		
		def myNugetReposList = [myNugetDev, myNugetRelease]
		def myNugetVirtualList = [myNugetReleaseVirtual, myNugetDevVirtual]
		def myNugetRemoteList = [myNugetRemote]
		def myNugetDevOpsPermissionList = [myNugetDev['key'], myNugetRelease['key']]
		def myNugetDevPermissionList = [myNugetDev['key']]
		def myNugetQAPermissionList = [myNugetDev['key'], myNugetRelease['key']]
}
