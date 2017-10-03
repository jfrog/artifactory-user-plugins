package data

import definitions.RepositoryLocalClass
import definitions.RepositoryVirtualClass

class RepositorySBT {
	def mysbtDev = new RepositoryLocalClass (
		key: 'sbt-dev-local',
		packageType: 'sbt',
		description: 'demo-sbt-development-path',
		repoLayoutRef: 'sbt-default',
		handleReleases: 'false',
		handleSnapshots: 'true')

	def mysbtRelease = new RepositoryLocalClass (
		key: 'sbt-release-local',
		packageType: 'sbt',
		description: 'demo-promotion-to-release-path',
		repoLayoutRef: 'sbt-default',
		handleReleases: 'true',
		handleSnapshots: 'false')

	def mysbtReleaseVirtual = new RepositoryVirtualClass (
		key: 'sbt-release-virtual',
		packageType: 'sbt',
		repositories:['sbt-release-local'],
		description: 'demo-release-virtual-repository',
		defaultDeploymentRepo : 'sbt-release-local'
		)

	def mysbtDevVirtual = new RepositoryVirtualClass (
		key: 'sbt-dev-virtual',
		packageType: 'sbt',
		repositories: ['sbt-dev-local'],
		description: 'demo-dev-virtual-repository',
		defaultDeploymentRepo : 'sbt-dev-local'
		)

	def mysbtReposList = [mysbtDev, mysbtRelease]
	def mysbtVirtualList = [mysbtReleaseVirtual, mysbtDevVirtual]
	def mysbtDevOpsPermissionList = [mysbtRelease['key']]
	def mysbtDevPermissionList = [mysbtDev['key']]
	def mysbtQAPermissionList = [mysbtDev['key'], mysbtRelease['key']]
}
