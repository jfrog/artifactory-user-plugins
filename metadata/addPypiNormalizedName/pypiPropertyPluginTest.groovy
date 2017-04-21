package org.jfrog.artifactory.client;
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import spock.lang.Specification;
import org.jfrog.artifactory.client.model.repository.settings.impl.PypiRepositorySettingsImpl;
import org.jfrog.artifactory.client.model.repository.settings.impl.MavenRepositorySettingsImpl;
import static org.jfrog.artifactory.client.ArtifactoryClient.create;
import groovyx.net.http.HttpResponseException;
import org.jfrog.artifactory.client.impl.ArtifactoryRequestImpl;

class pypiPropertyPluginTest extends Specification {
    private Logger logger = org.slf4j.LoggerFactory.getLogger(pypiPropertyPluginTest.class);
    def plugin = new File('./etc/plugins/pypiPropertyPlugin.groovy')
    def 'pypi property plugin test'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = create(baseurl, 'admin', 'password')
        def stream = new File('./src/test/groovy/binparse-test-1.2.tar.gz')
	logger.error('this is a error')
	def builder = artifactory.repositories().builders()
	def local = builder.localRepositoryBuilder().key('pypi-local')
	.repositorySettings(new PypiRepositorySettingsImpl()).build()
	artifactory.repositories().create(0, local) 

        artifactory.repository('pypi-local').upload('pypi-bug/binparse-test-1.2.tar.gz', stream).doUpload()

        when:
        artifactory.repository('pypi-local').file('pypi-bug/binparse-test-1.2.tar.gz').deleteProperty('pypi.name')
	artifactory.repository('pypi-local').file('pypi-bug/binparse-test-1.2.tar.gz').deleteProperty('pypi.normalized.name')
	artifactory.repository('pypi-local').file('pypi-bug/binparse-test-1.2.tar.gz').properties().addProperty('pypi.name', 'TEST_NAME').doSet()
	
	long timestamp = System.currentTimeMillis()
	try {plugin.setLastModified(timestamp)}
	catch(IOException e) {logger.error(e)}
	ArtifactoryRequest repoRequest = new ArtifactoryRequestImpl().apiUrl("api/plugins/reload")
	.method(ArtifactoryRequest.Method.POST)
	.responseType(ArtifactoryRequest.ContentType.TEXT);
	logger.error(artifactory.restCall(repoRequest))
	sleep(10000)

        then:
        artifactory.repository('pypi-local').file('pypi-bug/binparse-test-1.2.tar.gz').getPropertyValues('pypi.normalized.name')

        cleanup:
	artifactory.repository("pypi-local").delete()
    }

    def 'non pypi dir test'(){
	setup:
	def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = create(baseurl, 'admin', 'password')

        def builder = artifactory.repositories().builders()
        def local = builder.localRepositoryBuilder().key('maven-local')
        .repositorySettings(new MavenRepositorySettingsImpl()).build()
        artifactory.repositories().create(0, local)	

	def package1 = new File('./src/test/groovy/binparse-test-1.2.tar.gz')
	
	when:
	artifactory.repository('maven-local').upload('com/google/guava/1.2/binparse-test-1.2.tar.gz', package1).doUpload()
	
	long timestamp = System.currentTimeMillis()
	try {plugin.setLastModified(timestamp)}
	catch(IOException e) {logger.error(e)}
	ArtifactoryRequest repoRequest = new ArtifactoryRequestImpl().apiUrl("api/plugins/reload")
	.method(ArtifactoryRequest.Method.POST)
	.responseType(ArtifactoryRequest.ContentType.TEXT);
	logger.error(artifactory.restCall(repoRequest))
	sleep(10000)

	then:
	artifactory.repository('maven-local').file('com/google/guava/1.2/binparse-test-1.2.tar.gz').getPropertyValues('pypi.normalized.name') == null

	cleanup:
	artifactory.repository('maven-local').delete()

    }

    def 'pypi dir no pypi.name test'(){
	setup:
	def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = create(baseurl, 'admin', 'password')

        def builder = artifactory.repositories().builders()
        def local = builder.localRepositoryBuilder().key('pypi-local2')
        .repositorySettings(new PypiRepositorySettingsImpl()).build()
        artifactory.repositories().create(0, local)	

	def package1 = new File('./src/test/groovy/binparse-test-1.2.tar.gz')
	
	when:
	artifactory.repository('pypi-local2').upload('pypi-bug2/binparse-test-1.2.tar.gz', package1).doUpload()
	artifactory.repository('pypi-local2').file('pypi-bug2/binparse-test-1.2.tar.gz').deleteProperty('pypi.name')
	artifactory.repository('pypi-local2').file('pypi-bug2/binparse-test-1.2.tar.gz').deleteProperty('pypi.normalized.name')
     	
	long timestamp = System.currentTimeMillis()
	try {plugin.setLastModified(timestamp)}
	catch(IOException e) {logger.error(e)}
	ArtifactoryRequest repoRequest = new ArtifactoryRequestImpl().apiUrl("api/plugins/reload")
	.method(ArtifactoryRequest.Method.POST)
	.responseType(ArtifactoryRequest.ContentType.TEXT);
	logger.error(artifactory.restCall(repoRequest))
	sleep(10000)
 
	then:
	artifactory.repository('pypi-local2').file('pypi-bug2/binparse-test-1.2.tar.gz').getPropertyValues('pypi.normalized.name') == null

	cleanup:
	artifactory.repository('pypi-local2').delete()

    }

    def 'empty dir test'(){
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = create(baseurl, 'admin', 'password')

        def builder = artifactory.repositories().builders()
        def local = builder.localRepositoryBuilder().key('empty-test')
        .repositorySettings(new PypiRepositorySettingsImpl()).build()
        artifactory.repositories().create(0, local)

        when:
	long timestamp = System.currentTimeMillis()
	try {plugin.setLastModified(timestamp)}
	catch(IOException e) {logger.error(e)}
	ArtifactoryRequest repoRequest = new ArtifactoryRequestImpl().apiUrl("api/plugins/reload")
	.method(ArtifactoryRequest.Method.POST)
	.responseType(ArtifactoryRequest.ContentType.TEXT);
	logger.error(artifactory.restCall(repoRequest))
	sleep(10000)

        then:
        artifactory.repository('empty-test').file('pypi-bug2/binparse-test-1.2.tar.gz').getPropertyValues('pypi.normalized.name')== null

        cleanup:
        artifactory.repository('empty-test').delete()

    }


}
