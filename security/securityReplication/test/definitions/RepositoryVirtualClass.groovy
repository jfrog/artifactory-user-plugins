package definitions

class RepositoryVirtualClass {
	def key
	def rclass = 'virtual'
	def packageType
	def repositories
	def description
	def notes = 'created-by-automation'
	def includesPattern = '**/*'
	def excludesPattern = ''
	def debianTrivialLayout ='false'
	def artifactoryRequestsCanRetrieveRemoteArtifacts = 'false'
	def keyPair
	def pomRepositoryReferencesCleanupPolicy = 'discard_active_reference'
	def defaultDeploymentRepo
}
