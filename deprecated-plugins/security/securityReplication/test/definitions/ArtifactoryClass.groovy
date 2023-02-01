package definitions

class ArtifactoryClass {
	def company = ''				//company of users - used in Users class
	def artifactoryurl = ''			//artifactory URL - http://gcartifactory.jfrog.info/artifactory/
	def userid =''					//user id
	def password =''				//password
	def active = 'false'			//true - use this instance of artifactory of testing;  false - ignore

	String getCredential () {
		return userid + ":" + password;
	}
}
