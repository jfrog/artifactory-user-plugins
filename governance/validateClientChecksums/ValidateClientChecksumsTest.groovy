import spock.lang.Specification

import org.jfrog.artifactory.client.ArtifactoryClientBuilder

import org.apache.http.client.HttpResponseException


import java.net.HttpURLConnection
import java.net.URL

import org.jfrog.artifactory.client.model.repository.settings.impl.MavenRepositorySettingsImpl

class ValidateClientChecksumsTest extends Specification {
    def 'validateClientChecksum Test'() {
        setup:

        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = ArtifactoryClientBuilder.create().setUrl(baseurl)
            .setUsername('admin').setPassword('password').build()

        def builder = artifactory.repositories().builders()
        def local = builder.localRepositoryBuilder().key('local-repo')
            
            local.repositorySettings(new MavenRepositorySettingsImpl())

         
            artifactory.repositories().create(0, local.build())
        

        def repo = artifactory.repository('local-repo')
        def file1 = repo.upload("text.txt", new ByteArrayInputStream('helloworld'.bytes)).doUpload(); 

        URL new_url = new URL("http://localhost:8088/artifactory/local-repo/file.txt");
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


        when: 
                
        repo.download('file.txt').doDownload()
                     
        then:
        
         notThrown(HttpResponseException)
         
        when: 

        repo.download('text.txt').doDownload()

        then:

        thrown(HttpResponseException)

        cleanup:
        repo.delete()
       
        
     }
}
