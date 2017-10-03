package definitions

class ReplicationConfig {
	def url = ''
	def socketTimeoutMillis = '15000'
	def username =''
	def password =''
	def enableEventReplication = 'true'
	def enabled = 'true'
	def cronExp = '0 0 12 * * ?'
	def syncDeletes = 'true'
	def syncProperties = 'true'
	def repoKey = ''
}
