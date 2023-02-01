package devenv

import org.jfrog.artifactory.client.Artifactory
import org.jfrog.artifactory.client.ArtifactoryClientBuilder


/**
 * Created by stanleyf on 14/07/2017.
 * This code will be replaced by the new dev env framework
 */


class ArtifactoryClass {
    def artifactoryurl = ''			//artifactory URL - http://gcartifactory.jfrog.info/artifactory/
    def userid =''					//user id
    def password =''				//password
    def active = 'false'			//true - use this instance of artifactory of testing;  false - ignore

    String getCredential () {
        return userid + ":" + password;
    }
}


class ArtifactoryManager {

    def art1 = new ArtifactoryClass (
            artifactoryurl: 'http://localhost:8088/artifactory/',
            userid: 'admin',
            password: 'password',
            active: 'true'
    )


    def art2 = new ArtifactoryClass (
            artifactoryurl: 'http://localhost:8090/artifactory/',
            userid: 'admin',
            password: 'password',
            active: 'true'
    )

    // artifactory HA with docker compose
    def art3 = new ArtifactoryClass (
            artifactoryurl: 'http://localhost:8091/artifactory/',
            userid: 'admin',
            password: 'password',
            active: 'true'
    )

    def art4 = new ArtifactoryClass (
            artifactoryurl: 'http://localhost:8092/artifactory/',
            userid: 'admin',
            password: 'password',
            active: 'false'
    )

    def artifactoryList = [art1, art2, art3, art4]

    def artifactoryClientList = []

    ArtifactoryManager  () {
        getActiveArtifactory ();
    }

    private void getActiveArtifactory () {
        artifactoryList.each { art ->
            if (art['active'] == 'true') {
                artifactoryClientList << ArtifactoryClientBuilder.create().setUrl(art.artifactoryurl).setUsername(art.userid).setPassword(art.password).build()
            }
        }
    }

    public Artifactory[] getArtifactoryInstances () {
        return artifactoryClientList
    }

}
