package definitions

class RepositoryLocalClass {	
	def key
	def rclass ='local'
	def packageType
	def description
	def notes = 'created-by-automation'
	def includesPattern = '**/*'
	def excludesPattern = ''
	def repoLayoutRef
	def debianTrivialLayout = 'false'
	def checksumPolicyType = 'client-checksums'
	def handleReleases
	def handleSnapshots
	def maxUniqueSnapshots = '0'
	def snapshotVersionBehavior = 'unique'
	def suppressPomConsistencyChecks = 'false'
	def blackedOut = 'false'
	def archiveBrowsingEnabled = 'false'
	def calculateYumMetadata = 'false'
	def yumRootDepth = '0'
}
