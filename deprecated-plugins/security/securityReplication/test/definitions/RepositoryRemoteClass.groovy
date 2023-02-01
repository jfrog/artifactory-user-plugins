package definitions

class RepositoryRemoteClass {
	def key
	def rclass ='remote'
	def packageType
	def url
	def username =''
	def password = ''
	def proxy = ''
	def description
	def notes = 'created-by-automation'
	def includesPattern = '**/*'
	def excludesPattern =''
	def remoteRepoChecksumPolicyType = 'generate-if-absent'
	def handleReleases = 'true'
	def handleSnapshots = 'true'
	def maxUniqueSnapshots = 0
	def suppressPomConsistencyChecks = 'false'
	def hardFail = 'false'
	def offline = 'false'
	def blackedOut ='false'
	def storeArtifactsLocally = 'true'
	def socketTimeoutMillis = 15000
	def localAddress =''
	def retrievalCachePeriodSecs = 43200
	def failedRetrievalCachePeriodSecs = 30 
	def missedRetrievalCachePeriodSecs = 7200 
	def unusedArtifactsCleanupEnabled = 'false'
	def unusedArtifactsCleanupPeriodHours = 0
	def fetchJarsEagerly = 'false'
	def fetchSourcesEagerly = 'false'
	def shareConfiguration = 'false'
	def synchronizeProperties = 'false'
	def allowAnyHostAuth = 'false'
	def enableCookieManagement = 'false'
	def bowerRegistryUrl = 'https://bower.herokuapp.com'
	def vcsType = 'GIT'
	def vcsGitProvider = 'GITHUB'
	def vcsGitDownloadUrl = ''
}

