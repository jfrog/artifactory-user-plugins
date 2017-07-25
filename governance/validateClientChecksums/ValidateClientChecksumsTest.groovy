import spock.lang.Specification

import static org.jfrog.artifactory.client.ArtifactoryClient.create

import java.net.HttpURLConnection
import java.net.URL

import org.jfrog.artifactory.client.model.repository.settings.impl.MavenRepositorySettingsImpl

class ValidateClientChecksumsTest extends Specification {
    def 'validateClientChecksum Test'() {
        setup:

        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = create(baseurl, 'admin', 'password')

        def builder = artifactory.repositories().builders()

        // create 1st repo
        def local = builder.localRepositoryBuilder().key('local-repo')
            
            local.repositorySettings(new MavenRepositorySettingsImpl())

         
            artifactory.repositories().create(0, local.build())

           
        

        def repo = artifactory.repository('local-repo')
        // upload first file to first repo
        def file1 = repo.upload("text.txt", new ByteArrayInputStream('helloworld'.bytes)).doUpload(); 

        // create second repo
        def manual_local = builder.localRepositoryBuilder().key('manual-repo')
            
            manual_local.repositorySettings(new MavenRepositorySettingsImpl())

         
            artifactory.repositories().create(0, manual_local.build())
    

        // upload second file to second repo t    
        URL new_url = new URL("http://localhost:8088/artifactory/manual-repo/file.txt");
        URLConnection newConnection = new_url.openConnection();

        def auth = "Basic ${'admin:password'.bytes.encodeBase64()}"
        newConnection.setRequestProperty("Authorization", auth);


        newConnection.setDoOutput(true);
        newConnection.setRequestMethod("PUT");
        // pass checksums as headers 
        newConnection.setRequestProperty("X-Checksum-Sha1", "0bc2a0d661d056175b425e63d7aa3d0832d2a2ac");
        newConnection.setRequestProperty("X-Checksum-Md5", "a2ed931ecace715a6e61fb0ca273480a");
         OutputStreamWriter out = new OutputStreamWriter(
            newConnection.getOutputStream());
        out.write("Resource content");

        out.close();
        newConnection.getInputStream();



        // get file info from first and second repo
        def manual_local_repo = artifactory.repository('manual-repo').file('file.txt').info()
        def rest_local_repo = artifactory.repository('local-repo').file('text.txt').info()

         

        def manual_file_checksum = manual_local_repo.getChecksums().getMd5()

        def rest_file_checksum  = rest_local_repo.getOriginalChecksums().getMd5()


        
      
        when: 
            
            try {

            def rest_file_upload = repo.download('text.txt').doDownload()
            def manual_file_upload = manual_local.download('file.txt').doDownload()

                 
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
        artifactory.repository('manual-repo').delete()

    }
}
