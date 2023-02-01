package data

import definitions.RepositoryLocalClass

class RepositoryYum {
	def myyumDev = new RepositoryLocalClass (
		key: 'yum-dev-local',
		packageType: 'yum',
		description: "demo-yum-development-path",
		repoLayoutRef: 'simple-default',
		handleReleases: 'false',
		handleSnapshots: 'true')

		def myyumRelease = new RepositoryLocalClass (
		key: 'yum-release-local',
		packageType: 'yum',
		description: "demo-promotion-to-staging-path",
		repoLayoutRef: 'simple-default',
		handleReleases: 'true',
		handleSnapshots: 'false')

		def myyumReposList = [myyumDev, myyumRelease]
		def myyumDevOpsPermissionList = [myyumDev['key'], myyumRelease['key']]
		def myyumDevPermissionList = [myyumDev['key']]
		def myyumQAPermissionList = [myyumDev['key'], myyumRelease['key']]
}
