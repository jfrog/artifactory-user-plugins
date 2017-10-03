package data

import definitions.RepositoryLocalClass

class RepositoryVagrant {
	def myvagrantDev = new RepositoryLocalClass (
		key: 'vagrant-dev-local',
		packageType: 'vagrant',
		description: "demo-vagrant-development-path",
		repoLayoutRef: 'simple-default',
		handleReleases: 'false',
		handleSnapshots: 'true')

		def myvagrantRelease = new RepositoryLocalClass (
		key: 'vagrant-release-local',
		packageType: 'vagrant',
		description: "demo-promotion-to-release-path",
		repoLayoutRef: 'simple-default',
		handleReleases: 'true',
		handleSnapshots: 'false')

		def myvagrantReposList = [myvagrantDev, myvagrantRelease]
		def myvagrantDevOpsPermissionList = [myvagrantRelease['key']]
		def myvagrantDevPermissionList = [myvagrantDev['key']]
		def myvagrantQAPermissionList = [myvagrantDev['key'], myvagrantRelease['key']]
}
