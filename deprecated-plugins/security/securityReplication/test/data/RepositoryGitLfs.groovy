package data

import definitions.RepositoryLocalClass

class RepositoryGitLfs {
	def mygitlfsDev = new RepositoryLocalClass (
		key: 'gitlfs-dev-local',
		packageType: 'gitlfs',
		description: "gitlfs-development-path",
		repoLayoutRef: 'simple-default',
		handleReleases: 'false',
		handleSnapshots: 'true')

		def mygitlfsRelease = new RepositoryLocalClass (
		key: 'gitlfs-release-local',
		packageType: 'gitlfs',
		description: "development-to-release-path",
		repoLayoutRef: 'simple-default',
		handleReleases: 'true',
		handleSnapshots: 'false')

		def mygitlfsReposList = [mygitlfsDev, mygitlfsRelease]
		def mygitlfsDevOpsPermissionList = [mygitlfsDev['key'], mygitlfsRelease['key']]
		def mygitlfsDevPermissionList = [mygitlfsDev['key']]
		def mygitlfsQAPermissionList = [mygitlfsDev['key'], mygitlfsRelease['key']]
}
