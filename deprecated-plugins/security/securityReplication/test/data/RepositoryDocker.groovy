package data

import definitions.RepositoryLocalClass
import definitions.RepositoryRemoteClass
import definitions.RepositoryVirtualClass

class RepositoryDocker {
	def myDockerDev = new RepositoryLocalClass (
		key: 'docker-dev-local2',
		packageType: 'docker',
		description: "docker-dev-local2-configured-at-nginx",
		repoLayoutRef: 'simple-default',
		handleReleases: 'false',
		handleSnapshots: 'true')
	
	def myDockerRelease = new RepositoryLocalClass (
		key: 'docker-prod-local2',
		packageType: 'docker',
		description: "demo-promotion-to-release-path-configured-at-nginx",
		repoLayoutRef: 'simple-default',
		handleReleases: 'true',
		handleSnapshots: 'false')
	
	def myDockerRemote = new RepositoryRemoteClass (
		key: 'docker-remote',
		packageType: 'docker',
		url: 'https://registry-1.docker.io/',
		description: 'Docker-remote-repository',
		handleReleases: 'true',
		handleSnapshots: 'true')
	
	def myDockerBintrayArt = new RepositoryRemoteClass (
		key: 'docker-bintray-artifactory',
		packageType: 'docker',
		url: 'https://jfrog-docker-reg2.bintray.io/',
		description: 'Docker-remote-repository-Artifactory',
		handleReleases: 'true',
		handleSnapshots: 'true')
	
	def myDockerVirtual = new RepositoryVirtualClass (
		key: 'docker-virtual',
		packageType: 'docker',
		repositories: ['docker-dev-local2', 'docker-prod-local2', 'docker-remote', 'docker-bintray-artifactory'],
		description: 'demo-release-virtual-repository',
		defaultDeploymentRepo : 'docker-dev-local2'
		)
	
	def myDockerReposList = [myDockerDev, myDockerRelease]
	def myDockerVirtualList = [myDockerVirtual]
	def myDockerRemoteList = [myDockerRemote, myDockerBintrayArt]
	def myDockerDevOpsPermissionList = [myDockerDev['key'], myDockerRelease['key']]
	def myDockerDevPermissionList = [myDockerDev['key']]
	def myDockerQAPermissionList = [myDockerDev['key'], myDockerRelease['key']]
}
