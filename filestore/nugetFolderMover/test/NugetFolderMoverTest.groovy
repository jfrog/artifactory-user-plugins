import spock.lang.Specification
import org.jfrog.artifactory.client.model.repository.settings.impl.NugetRepositorySettingsImpl
import org.jfrog.artifactory.client.ArtifactoryClientBuilder

class NugetFolderMoverTest extends Specification {
    def 'nugetFolderMover Test'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = ArtifactoryClientBuilder.create().setUrl(baseurl)
            .setUsername('admin').setPassword('password').build()

        def builder = artifactory.repositories().builders()
        def local = builder.localRepositoryBuilder().key('nuget-local')
          .repositorySettings(new NugetRepositorySettingsImpl()).build()
        artifactory.repositories().create(0, local)
        def repo = artifactory.repository('nuget-local')

        when:
        def file = new File('./src/test/groovy/NugetFolderMoverTest/angularjs.1.4.8.nupkg')
        def name = 'angularjs.1.4.8.nupkg'
        repo.upload(name, file).doUpload()
        sleep(5000)

        then:
        repo.file("/angularjs/angularjs/angularjs.1.4.8.nupkg").info()

        cleanup:
        artifactory.repository("nuget-local").delete()
    }
}
