package data

import definitions.RepositoryLocalClass


class RepositoryDebian {

	def mydebianDev = new RepositoryLocalClass (
		key: 'debian-dev-local',
		packageType: 'debian',
		description: "debian-development-path",
		repoLayoutRef: 'simple-default',
		handleReleases: 'false',
		handleSnapshots: 'true')

		def mydebianRelease = new RepositoryLocalClass (
		key: 'debian-release-local',
		packageType: 'debian',
		description: "development-to-release-path",
		repoLayoutRef: 'simple-default',
		handleReleases: 'true',
		handleSnapshots: 'false')

		def mydebianReposList = [mydebianDev, mydebianRelease]
		def mydebianDevOpsPermissionList = [mydebianDev['key'], mydebianRelease['key']]
		def mydebianDevPermissionList = [mydebianDev['key']]
		def mydebianQAPermissionList = [mydebianDev['key'], mydebianRelease['key']]

}
