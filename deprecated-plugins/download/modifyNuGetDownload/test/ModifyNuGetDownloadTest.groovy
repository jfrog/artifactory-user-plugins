import spock.lang.Specification

import org.jfrog.artifactory.client.model.repository.settings.impl.NugetRepositorySettingsImpl
import org.jfrog.artifactory.client.ArtifactoryClientBuilder

class ModifyNuGetDownloadTest extends Specification {
    def 'modify NuGet download test'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = ArtifactoryClientBuilder.create().setUrl(baseurl)
            .setUsername('admin').setPassword('password').build()
        def nupkgfile = new File('./src/test/groovy/ModifyNuGetDownloadTest/nugethello.1.0.0.nupkg')

        def builder = artifactory.repositories().builders()
        def local = builder.localRepositoryBuilder().key("nuget-gallery").repositorySettings(new NugetRepositorySettingsImpl())
        try {
            artifactory.repositories().create(0, local.build())
        }
        catch(HttpResponseException) {
            println "repo already exist"
        }

        // upload test file, 2 levels deeper
        artifactory.repository("nuget-gallery")
            .upload("nugethello/nugethello/nugethello.1.0.0.nupkg", nupkgfile)
            .doUpload()
        
        when:
        // request file
        def reader = artifactory.repository("nuget-gallery")
                .download("nugethello.1.0.0.nupkg")
                .doDownload();
        def downloadFile = reader.text

        then:
        // should find file two levels down the requested path
        downloadFile
        
        cleanup:
        // remove test file
        println "deleted test file from artifactory"
        artifactory.repository("nuget-gallery").delete("nugethello/nugethello/nugethello.1.0.0.nupkg")
        // remove repo if it's not there originally

        def files = artifactory.searches().repositories("nuget-gallery").artifactsByName("*.*").doSearch().size()
        if(files == 0) {
            artifactory.repository("nuget-gallery").delete()
        }
        println "deleted test repo from artifactory"
    }
}
