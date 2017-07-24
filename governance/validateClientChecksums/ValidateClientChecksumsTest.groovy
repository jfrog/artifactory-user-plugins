import spock.lang.Specification

import static org.jfrog.artifactory.client.ArtifactoryClient.create

import org.jfrog.artifactory.client.model.repository.settings.impl.MavenRepositorySettingsImpl

class ValidateClientChecksumsTest extends Specification {
    def 'validateClientChecksum Test'() {
        setup:

        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = create(baseurl, 'admin', 'password')

        def builder = artifactory.repositories().builders()

        def local = builder.localRepositoryBuilder().key('local-repo')
            
            local.repositorySettings(new MavenRepositorySettingsImpl())

         
            artifactory.repositories().create(0, local.build())

           
        

        def repo = artifactory.repository('local-repo')
        def manual_file = artifactory.repository('maven-local')
        def file1 = repo.upload("text.txt", new ByteArrayInputStream('helloworld'.bytes)).doUpload();    

        
        def manual_local_repo = artifactory.repository('maven-local').file('pom.xml').info()
        def rest_local_repo = artifactory.repository('local-repo').file('text.txt').info()

         

        def manual_file_checksum = manual_local_repo.getChecksums().getMd5()

        def rest_file_checksum  = rest_local_repo.getOriginalChecksums().getMd5()
        
      
        when: 
            
            try {
            def manual_file_upload = manual_file.download('pom.xml').doDownload()
            def rest_file_upload = repo.download('text.txt').doDownload()
                 }   
            catch(HttpResponseException) {
                if(HttpResponseException.getStatusCode() == 409) {
                    println "print failed because original checksum is null"
                }
            }
                     
        then:
        
         
        assert manual_file_checksum != null
        assert rest_file_checksum == null


        
        
        cleanup:
        artifactory.repository('local-repo').delete()
    }
}
