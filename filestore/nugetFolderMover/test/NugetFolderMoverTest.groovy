import spock.lang.Specification
import org.jfrog.artifactory.client.model.repository.settings.impl.NugetRepositorySettingsImpl
import static org.jfrog.artifactory.client.ArtifactoryClient.create

class NugetFolderMoverTest extends Specification {
    def 'nugetFolderMover Test'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = create(baseurl, 'admin', 'password')
        def auth = "Basic ${'admin:password'.bytes.encodeBase64().toString()}"

        def builder = artifactory.repositories().builders()
        def local = builder.localRepositoryBuilder().key('nuget-local')
          .repositorySettings(new NugetRepositorySettingsImpl()).build()
        artifactory.repositories().create(0, local)
        def repo = artifactory.repository('nuget-local')

        def file = new File('./src/test/groovy/NugetFolderMoverTest/angularjs.1.4.8.nupkg')
        def name = 'angularjs.1.4.8.nupkg'
        repo.upload(name, file).doUpload()

        when:
        artifactory.plugins().execute('folderMover')
        def conn = new URL("${baseurl}").openConnection()

        then:
        sleep(30000)
        repo.file("/angularjs/angularjs/1.4.8/angularjs-1.4.8.nupkg").info()

        cleanup:
        artifactory.repository("nuget-local").delete()
    }
}
