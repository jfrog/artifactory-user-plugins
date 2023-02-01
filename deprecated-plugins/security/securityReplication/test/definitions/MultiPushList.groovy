package definitions

class MultiPushList {
	def cronExp = '0 0 12 * * ?'
	def enableEventReplication = 'true'
	def replications = [] as MultiPushRepo
}
