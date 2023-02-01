package data

import definitions.RepositoryLocalClass
import definitions.RepositoryRemoteClass
import definitions.RepositoryVirtualClass

class RepositoryPython {
	def mypypiDev = new RepositoryLocalClass (
		key: 'pypi-dev-local',
		packageType: 'pypi',
		description: "demo-pypi-development-path",
		repoLayoutRef: 'simple-default',
		handleReleases: 'false',
		handleSnapshots: 'true')
	
	def mypypiRelease = new RepositoryLocalClass (
		key: 'pypi-release-local',
		packageType: 'pypi',
		description: "demo-promotion-to-release-path",
		repoLayoutRef: 'simple-default',
		handleReleases: 'true',
		handleSnapshots: 'false')
	
	def mypypiRemote = new RepositoryRemoteClass (
		key: 'pypi-remote',
		packageType: 'pypi',
		url: 'https://pypi.python.org',
		description: 'pypi-remote-repository',
		handleReleases: 'true',
		handleSnapshots: 'true')
	
	def mypypiReleaseVirtual = new RepositoryVirtualClass (
		key: 'pypi-release-virtual',
		packageType: 'pypi',
		repositories:['pypi-release-local','pypi-remote'],
		description: 'demo-release-virtual-repository',
		defaultDeploymentRepo : 'pypi-release-local'
		)
	
	def mypypiDevVirtual = new RepositoryVirtualClass (
		key: 'pypi-dev-virtual',
		packageType: 'pypi',
		repositories: ['pypi-dev-local','pypi-remote'],
		description: 'demo-snapshot-virtual-repository',
		defaultDeploymentRepo : 'pypi-dev-local'
		)
	
	def mypypiReposList = [mypypiDev, mypypiRelease]
	def mypypiVirtualList = [mypypiReleaseVirtual, mypypiDevVirtual]
	def mypypiRemoteList = [mypypiRemote]
	def mypypiDevOpsPermissionList = [mypypiRelease['key']]
	def mypypiDevPermissionList = [mypypiDev['key']]
	def mypypiQAPermissionList = [mypypiDev['key'], mypypiRelease['key']]
	
}
