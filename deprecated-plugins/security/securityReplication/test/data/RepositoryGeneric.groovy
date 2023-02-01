package data

import definitions.RepositoryLocalClass
import definitions.RepositoryVirtualClass

class RepositoryGeneric {
	def mygenericDev = new RepositoryLocalClass (
		key: 'generic-dev-local',
		packageType: 'generic',
		description: "generic-development-path",
		repoLayoutRef: 'simple-default',
		handleReleases: 'false',
		handleSnapshots: 'true')

		def mygenericRelease = new RepositoryLocalClass (
		key: 'generic-release-local',
		packageType: 'generic',
		description: "development-to-release-path",
		repoLayoutRef: 'simple-default',
		handleReleases: 'true',
		handleSnapshots: 'false')

		def mygenericReleaseVirtual = new RepositoryVirtualClass (
		key: 'generic-release-virtual',
		packageType: 'generic',
		repositories:['generic-release-local'],
		description: 'release-virtual-repository',
		defaultDeploymentRepo : 'generic-release-local'
		)

		def mygenericDevVirtual = new RepositoryVirtualClass (
		key: 'generic-dev-virtual',
		packageType: 'generic',
		repositories: ['generic-dev-local'],
		description: 'dev-virtual-repository',
		defaultDeploymentRepo : 'generic-dev-local'
		)

		def mygenericReposList = [mygenericDev, mygenericRelease]
		def mygenericVirtualList = [mygenericReleaseVirtual, mygenericDevVirtual]
		def mygenericDevOpsPermissionList = [mygenericDev['key'], mygenericRelease['key']]
		def mygenericDevPermissionList = [mygenericDev['key']]
		def mygenericQAPermissionList = [mygenericDev['key'], mygenericRelease['key']]
}
