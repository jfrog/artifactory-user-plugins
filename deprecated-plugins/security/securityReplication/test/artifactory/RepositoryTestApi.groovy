package artifactory

import org.apache.http.client.HttpResponseException
import org.jfrog.artifactory.client.Artifactory
import data.RepositoryList
import definitions.RepositoryClass
import definitions.Constants
import org.jfrog.artifactory.client.ArtifactoryRequest
import org.jfrog.artifactory.client.impl.ArtifactoryRequestImpl
import org.jfrog.artifactory.client.model.Repository


/**
 * Created by stanleyf on 11/19/16.
 */
class RepositoryTestApi {
    final static Constants r = new Constants();
    static Artifactory art
    static RepositoryList repoList = new RepositoryList()


    RepositoryTestApi(Artifactory art) {
        this.art = art
    }

    static def createRepositoryLocal() {
        repoList.getLocalRepoList().each { localRepo ->
            createRepo (localRepo)
        }
    }

    static def deleteRepositoryLocal() {
        repoList.getLocalRepoList().each { localRepo ->
           deleteRepo (localRepo)
        }
    }

    static def createRepositoriesRemote () {
        repoList.getRemoteRepoList().each { remoteRepo ->
            createRepo (remoteRepo)
        }
    }

    static def deleteRepositoryRemote() {
        repoList.getRemoteRepoList().each { remoteRepo ->
            deleteRepo (remoteRepo)
        }
    }

    static def createRepositoriesVirtual () {
        repoList.getVirtualRepoList().each { virtualRepo ->
            createRepo (virtualRepo)
        }
    }

    static def deleteRepositoryVirtual() {
        repoList.getVirtualRepoList().each { virtualRepo ->
            deleteRepo(virtualRepo)
        }
    }

    static def RepositoryClass[] getRepositories(def repoType) {
        ArtifactoryRequest repositoryRequest = new ArtifactoryRequestImpl().apiUrl('api/repositories')
                .method(ArtifactoryRequest.Method.GET)
                .responseType(ArtifactoryRequest.ContentType.JSON)

        def response = new groovy.json.JsonSlurper().parseText( art.restCall(repositoryRequest).getRawBody()) as ArrayList<Map>
        return response.findAll { it.type == repoType }.collect {
            [key: (it.key).toLowerCase()]
        } as RepositoryClass[]
    }

//######################################################################
//######## GENERAL  ####################################################
//######################################################################

    private static def createRepo (def repoList) {
        repoList.each { repolisteach ->
            repositoryRequest(repolisteach, repolisteach['key'] as String)
        }
    }

    private static def deleteRepo (def repoList) {
        repoList.each { repolisteach ->
            def result = art.repository("${repolisteach['key']}").delete()
            assert result
        }
    }

    private static def repositoryRequest(def requestBody, String repoKey) {
        if (respositoryExists(repoKey)) {
            return
        }

        ArtifactoryRequest repositoryrequest = new ArtifactoryRequestImpl().apiUrl("api/repositories/${repoKey}")
                .method(ArtifactoryRequest.Method.PUT)
                .requestBody(requestBody)
                .requestType(ArtifactoryRequest.ContentType.JSON)
                .responseType(ArtifactoryRequest.ContentType.ANY)
        try {
            art.restCall(repositoryrequest)
        } catch (HttpResponseException hre) {
            System.out.println("Could not create ${repoKey} repository with status ${hre.getStatusCode()}, ${hre.getMessage()} -  Retrying...")
            art.repository(repoKey).delete()
            art.restCall(repositoryrequest)
        }
    }

    private static def respositoryExists (String repoKey) {
        try {
            art.repository(repoKey).get()
        } catch (HttpResponseException hre) {
            return false
        }
        return true
    }
}
