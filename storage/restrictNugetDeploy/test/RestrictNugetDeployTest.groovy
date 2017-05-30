import spock.lang.Specification
import org.jfrog.artifactory.client.model.repository.settings.impl.NugetRepositorySettingsImpl
import groovyx.net.http.HttpResponseException
import static org.jfrog.artifactory.client.ArtifactoryClient.create

class RestrictNugetDeployTest extends Specification {
    def 'restrict nuget deploy test'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = create(baseurl, 'admin', 'password')

        def builder = artifactory.repositories().builders()
        def local = builder.localRepositoryBuilder().key('nuget-local')
        .repositorySettings(new NugetRepositorySettingsImpl()).build()
        artifactory.repositories().create(0, local)

        def remote = builder.remoteRepositoryBuilder().key('nuget-remote')
        remote.repositorySettings(new NugetRepositorySettingsImpl())
        remote.url('https://nuget.org')
        artifactory.repositories().create(0, remote.build())

        def jquery = new File('./src/test/groovy/RestrictNugetDeployTest/jquery.3.1.1.nupkg')
        
        when:
        artifactory.repository('nuget-local').upload('jQuery Foundation, Inc./jQuery/jQuery.3.1.1.nupkg', jquery).doUpload()
        
        then:
        thrown(HttpResponseException)

        when:
        artifactory.repository('nuget-local').upload('jQuery Foundation, Inc./new/new.3.1.1.nupkg', jquery).doUpload()
    
        then:
        notThrown(HttpResponseException)

        cleanup:
        artifactory.repository('nuget-local').delete()
        artifactory.repository('nuget-remote').delete()
    }
}
