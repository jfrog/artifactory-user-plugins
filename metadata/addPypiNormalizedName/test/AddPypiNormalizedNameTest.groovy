import org.jfrog.artifactory.client.model.repository.settings.impl.MavenRepositorySettingsImpl
import org.jfrog.artifactory.client.model.repository.settings.impl.PypiRepositorySettingsImpl
import spock.lang.Specification

import org.jfrog.artifactory.client.ArtifactoryClientBuilder

class AddPypiNormalizedNameTest extends Specification {
    def 'pypi normalized name test'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = ArtifactoryClientBuilder.create().setUrl(baseurl)
            .setUsername('admin').setPassword('password').build()
        def stream = new File('./src/test/groovy/AddPypiNormalizedNameTest/binparse-1.2.tar.gz')
        def builder = artifactory.repositories().builders()
        def local = builder.localRepositoryBuilder().key('pypi-local')
        .repositorySettings(new PypiRepositorySettingsImpl()).build()
        artifactory.repositories().create(0, local)
        artifactory.repository('pypi-local').upload('pypi-bug/binparse-test-1.2.tar.gz', stream).doUpload()

        when:
        artifactory.repository('pypi-local').file('pypi-bug/binparse-test-1.2.tar.gz').deleteProperty('pypi.name')
        artifactory.repository('pypi-local').file('pypi-bug/binparse-test-1.2.tar.gz').deleteProperty('pypi.normalized.name')
        artifactory.repository('pypi-local').file('pypi-bug/binparse-test-1.2.tar.gz').properties().addProperty('pypi.name', 'TEST_NAME').doSet()
        sleep(5000)

        then:
        artifactory.repository('pypi-local').file('pypi-bug/binparse-test-1.2.tar.gz').getPropertyValues('pypi.normalized.name')[0] == 'test-name'

        cleanup:
        artifactory.repository("pypi-local").delete()
    }

    def 'pypi normalized name remote cache test'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = ArtifactoryClientBuilder.create().setUrl(baseurl)
            .setUsername('admin').setPassword('password').build()
        def stream = new File('./src/test/groovy/AddPypiNormalizedNameTest/binparse-1.2.tar.gz')
        def builder = artifactory.repositories().builders()
        def local = builder.localRepositoryBuilder().key('pypi-local')
            .repositorySettings(new PypiRepositorySettingsImpl()).build()
        def remote = builder.remoteRepositoryBuilder().key('pypi-remote')
            .url('http://localhost:8081/artifactory/api/pypi/pypi-local/')
            .repositorySettings(new PypiRepositorySettingsImpl()).build()
        artifactory.repositories().create(0, local)
        artifactory.repositories().create(0, remote)
        artifactory.repository('pypi-local').upload('pypi-bug/binparse-test-1.2.tar.gz', stream).doUpload()
        artifactory.repository('pypi-remote').download('pypi-bug/binparse-test-1.2.tar.gz').doDownload()

        when:
        artifactory.repository('pypi-remote-cache').file('pypi-bug/binparse-test-1.2.tar.gz').properties().addProperty('pypi.name', 'TEST_NAME').doSet()
        sleep(5000)

        then:
        artifactory.repository('pypi-remote-cache').file('pypi-bug/binparse-test-1.2.tar.gz').getPropertyValues('pypi.normalized.name')[0] == 'test-name'

        cleanup:
        artifactory.repository("pypi-remote").delete()
        artifactory.repository("pypi-local").delete()
    }

    def 'non pypi dir test'(){
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = ArtifactoryClientBuilder.create().setUrl(baseurl)
            .setUsername('admin').setPassword('password').build()
        def builder = artifactory.repositories().builders()
        def local = builder.localRepositoryBuilder().key('maven-local')
        .repositorySettings(new MavenRepositorySettingsImpl()).build()
        artifactory.repositories().create(0, local)
        def package1 = new File('./src/test/groovy/AddPypiNormalizedNameTest/binparse-1.2.tar.gz')

        when:
        artifactory.repository('maven-local').upload('com/google/guava/1.2/binparse-test-1.2.tar.gz', package1).doUpload()
        sleep(5000)

        then:
        artifactory.repository('maven-local').file('com/google/guava/1.2/binparse-test-1.2.tar.gz').getPropertyValues('pypi.normalized.name') == null

        cleanup:
        artifactory.repository('maven-local').delete()
    }

    def 'pypi dir no pypi.name test'(){
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = ArtifactoryClientBuilder.create().setUrl(baseurl)
            .setUsername('admin').setPassword('password').build()
        def builder = artifactory.repositories().builders()
        def local = builder.localRepositoryBuilder().key('pypi-local2')
        .repositorySettings(new PypiRepositorySettingsImpl()).build()
        artifactory.repositories().create(0, local)
        def package1 = new File('./src/test/groovy/AddPypiNormalizedNameTest/binparse-1.2.tar.gz')

        when:
        artifactory.repository('pypi-local2').upload('pypi-bug2/binparse-test-1.2.tar.gz', package1).doUpload()
        artifactory.repository('pypi-local2').file('pypi-bug2/binparse-test-1.2.tar.gz').deleteProperty('pypi.name')
        artifactory.repository('pypi-local2').file('pypi-bug2/binparse-test-1.2.tar.gz').deleteProperty('pypi.normalized.name')
        sleep(5000)

        then:
        artifactory.repository('pypi-local2').file('pypi-bug2/binparse-test-1.2.tar.gz').getPropertyValues('pypi.normalized.name') == null

        cleanup:
        artifactory.repository('pypi-local2').delete()
    }
}
