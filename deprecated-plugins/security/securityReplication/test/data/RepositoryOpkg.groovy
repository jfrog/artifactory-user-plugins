package data

import definitions.RepositoryLocalClass

class RepositoryOpkg {
	def myopkgDev = new RepositoryLocalClass (
		key: 'opkg-dev-local',
		packageType: 'opkg',
		description: "demo-opkg-development-path",
		repoLayoutRef: 'simple-default',
		handleReleases: 'false',
		handleSnapshots: 'true')

		def myopkgRelease = new RepositoryLocalClass (
		key: 'opkg-release-local',
		packageType: 'opkg',
		description: "demo-promotion-to-release-path",
		repoLayoutRef: 'simple-default',
		handleReleases: 'true',
		handleSnapshots: 'false')

		def myopkgReposList = [myopkgDev, myopkgRelease]
		def myopkgDevOpsPermissionList = [myopkgRelease['key']]
		def myopkgDevPermissionList = [myopkgDev['key']]
		def myopkgQAPermissionList = [myopkgDev['key'], myopkgRelease['key']]
}
