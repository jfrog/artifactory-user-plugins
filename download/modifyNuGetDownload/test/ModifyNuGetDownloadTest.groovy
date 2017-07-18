import spock.lang.Specification

import org.jfrog.artifactory.client.model.repository.settings.impl.NugetRepositorySettingsImpl
import static org.jfrog.artifactory.client.ArtifactoryClient.create

class ModifyNuGetDownloadTest extends Specification {
    def 'test name'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = create(baseurl, 'admin', 'password')
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
        
        // request file
        when:
        def reader = artifactory.repository("nuget-gallery")
                .download("nugethello.1.0.0.nupkg")
                .doDownload();
        def downloadFile = reader.text
        //repositoryRequest(request)
        //sleep(3000)\

        then:
        // should find file two levels down the requested path
        downloadFile
        // try
        
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
