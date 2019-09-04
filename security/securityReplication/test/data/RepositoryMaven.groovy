package data

import definitions.RepositoryLocalClass
import definitions.RepositoryRemoteClass
import definitions.RepositoryVirtualClass

class RepositoryMaven {

	def myMavenSnapshot = new RepositoryLocalClass (
		key: 'maven-data-snapshot-local',
		packageType: 'maven',
		description: 'demo-maven-development-path',
		repoLayoutRef: 'maven-2-default',
		handleReleases: 'false',
		handleSnapshots: 'true')
	
	def myMavenStaging = new RepositoryLocalClass (
		key: 'maven-data-staging-local',
		packageType: 'maven',
		description: 'demo-promotion-to-staging-path',
		repoLayoutRef: 'maven-2-default',
		handleReleases: 'true',
		handleSnapshots: 'false')
	
	def myMavenRelease = new RepositoryLocalClass (
		key: 'maven-data-release-local',
		packageType: 'maven',
		description: 'demo-promotion-to-release-path',
		repoLayoutRef: 'maven-2-default',
		handleReleases: 'true',
		handleSnapshots: 'false')

	def myMavenAutomationRelease = new RepositoryLocalClass (
			key: 'automation-mvn-solution-local',
			packageType: 'maven',
			description: 'demo-promotion-to-release-path',
			repoLayoutRef: 'maven-2-default',
			handleReleases: 'true',
			handleSnapshots: 'false')

	def myMavenAutomationSnapshot = new RepositoryLocalClass (
			key: 'automation-mvn-sol-snapshot-local',
			packageType: 'maven',
			description: 'demo-promotion-to-release-path',
			repoLayoutRef: 'maven-2-default',
			handleReleases: 'false',
			handleSnapshots: 'true')

	def jcenter = new RepositoryRemoteClass (
		key: 'jcenter',
		packageType: 'maven',
		url: 'https://jcenter.bintray.com',
		description: 'jcenter-remote-repository',
		handleReleases: 'true',
		handleSnapshots: 'true')


	def myMavenReleaseVirtual = new RepositoryVirtualClass (
		key: 'maven-release-virtual',
		packageType: 'maven',
		repositories:['maven-data-release-local','maven-data-staging-local','jcenter'],
		description: 'demo-release-virtual-repository',
		defaultDeploymentRepo : 'maven-data-staging-local'
		)

	def myMavenDevVirtual = new RepositoryVirtualClass (
		key: 'maven-snapshot-virtual',
		packageType: 'maven',
		repositories: ['maven-data-snapshot-local','jcenter'],
		description: 'demo-snapshot-virtual-repository',
		defaultDeploymentRepo : 'maven-data-snapshot-local'
		)

	def myMavenAutomationVRelease = new RepositoryVirtualClass(
		key: 'libs-release',
		packageType: 'maven',
		repositories: ['automation-mvn-solution-local', 'jcenter'],
		description: 'repository for maven build from the swampup example',
		defaultDeploymentRepo : 'automation-mvn-solution-local'
	)

	def myMavenAutomationVSnapshot = new RepositoryVirtualClass(
		key: 'libs-snapshot',
		packageType: 'maven',
		repositories: ['automation-mvn-sol-snapshot-local', 'jcenter'],
		description: 'repository for maven build from the swampup example',
		defaultDeploymentRepo : 'automation-mvn-sol-snapshot-local'
	)

	def myMavenReposList = [myMavenSnapshot, myMavenStaging, myMavenRelease, myMavenAutomationRelease, myMavenAutomationSnapshot]
	def myMavenRemoteList = [jcenter]
	def myMavenVirtualList = [myMavenReleaseVirtual, myMavenDevVirtual, myMavenAutomationVRelease,myMavenAutomationVSnapshot ]
	def myMavenDevOpsPermissionList = [myMavenStaging['key'], myMavenRelease['key']]
	def myMavenDevPermissionList = [myMavenSnapshot['key']]
	def myMavenQAPermissionList = [myMavenSnapshot['key'], myMavenStaging['key'], myMavenRelease['key']]
}
