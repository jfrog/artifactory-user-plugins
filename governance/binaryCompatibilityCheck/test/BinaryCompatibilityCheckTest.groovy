import spock.lang.Specification
import groovy.json.JsonSlurper

import org.jfrog.artifactory.client.ArtifactoryClientBuilder
import org.jfrog.artifactory.client.model.repository.settings.impl.MavenRepositorySettingsImpl

class BinaryCompatibilityCheckTest extends Specification {
    def 'compatible binary compatibility check test'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = ArtifactoryClientBuilder.create().setUrl(baseurl)
            .setUsername('admin').setPassword('password').build()

        def builder = artifactory.repositories().builders()
        def local = builder.localRepositoryBuilder().key('maven-local')
        .repositorySettings(new MavenRepositorySettingsImpl()).build()
        artifactory.repositories().create(0, local)
        
        def jarfile1 = new File('./src/test/groovy/BinaryCompatibilityCheckTest/guava-16.0.jar')
        def jarfile2 = new File('./src/test/groovy/BinaryCompatibilityCheckTest/guava-16.0.1.jar')

        when:
        artifactory.repository('maven-local').upload('com/google/guava/guava/16.0/guava-16.0.jar', jarfile1).doUpload()
        artifactory.repository('maven-local').upload('com/google/guava/guava/16.0.1/guava-16.0.1.jar', jarfile2).doUpload()

        def auth = "Basic ${'admin:password'.bytes.encodeBase64()}"
        def conn = new URL("${baseurl}/api/storage/maven-local/com/google/guava/guava/16.0.1/guava-16.0.1.jar?properties").openConnection()
        conn.requestMethod = 'GET'
        conn.setRequestProperty('Authorization', auth)
        assert conn.responseCode == 200
        def json = new JsonSlurper().parse(conn.getInputStream())
        conn.disconnect()

        then:
        json.properties['approve.binaryCompatibleWith'] == ['16.0']

        cleanup:
        artifactory.repository('maven-local').delete()
    }

    def 'incompatible binary compatibility check test'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = ArtifactoryClientBuilder.create().setUrl(baseurl)
            .setUsername('admin').setPassword('password').build()

        def builder = artifactory.repositories().builders()
        def local = builder.localRepositoryBuilder().key('maven-local')
        .repositorySettings(new MavenRepositorySettingsImpl()).build()
        artifactory.repositories().create(0, local)
        
        def jarfile1 = new File('./src/test/groovy/BinaryCompatibilityCheckTest/guava-10.0.jar')
        def jarfile2 = new File('./src/test/groovy/BinaryCompatibilityCheckTest/guava-10.1.jar')

        when:
        artifactory.repository('maven-local').upload('com/google/guava/guava/10.0/guava-10.0.jar', jarfile1).doUpload()
        artifactory.repository('maven-local').upload('com/google/guava/guava/10.1/guava-10.1.jar', jarfile2).doUpload()

        def auth = "Basic ${'admin:password'.bytes.encodeBase64()}"
        def conn = new URL("${baseurl}/api/storage/maven-local/com/google/guava/guava/10.1/guava-10.1.jar?properties").openConnection()
        conn.requestMethod = 'GET'
        conn.setRequestProperty('Authorization', auth)
        assert conn.responseCode == 200
        def json = new JsonSlurper().parse(conn.getInputStream())
        conn.disconnect()

        then:
        json.properties['approve.binaryIncompatibleWith'] == ['10.0']

        cleanup:
        artifactory.repository('maven-local').delete()
    }
}
