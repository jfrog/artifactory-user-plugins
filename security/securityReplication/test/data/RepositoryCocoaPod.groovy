package data

import definitions.RepositoryLocalClass
import definitions.RepositoryRemoteClass

class RepositoryCocoaPod {
	def mycocoapodDev = new RepositoryLocalClass (
		key: 'cocoapod-dev-local',
		packageType: 'cocoapod',
		description: "cocoapod-development-path",
		repoLayoutRef: 'simple-default',
		handleReleases: 'false',
		handleSnapshots: 'true')

		def mycocoapodRelease = new RepositoryLocalClass (
		key: 'cocoapod-release-local',
		packageType: 'cocoapod',
		description: "development-to-release-path",
		repoLayoutRef: 'simple-default',
		handleReleases: 'true',
		handleSnapshots: 'false')

		def mycocoapodRemote = new RepositoryRemoteClass (
			key: 'cocoapod-remote',
			packageType: 'cocoapod',
			url: 'https://github.com/',
			description: 'cocoapod-remote-repository',
			handleReleases: 'true',
			handleSnapshots: 'true')

		def mycocoapodReposList = [mycocoapodDev, mycocoapodRelease]
		def mycocoapodRemoteList = [mycocoapodRemote]
		def mycocoapodDevOpsPermissionList = [mycocoapodDev['key'], mycocoapodRelease['key']]
		def mycocoapodDevPermissionList = [mycocoapodDev['key']]
		def mycocoapodQAPermissionList = [mycocoapodDev['key'], mycocoapodRelease['key']]
}
