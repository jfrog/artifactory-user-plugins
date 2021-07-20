package common

import artifactory.SecurityTestApi
import org.apache.http.client.HttpResponseException
import org.apache.http.HttpException
import org.jfrog.artifactory.client.Artifactory
import org.jfrog.artifactory.client.ArtifactoryClientBuilder
import org.jfrog.artifactory.client.model.User

/**
 * Created by stanleyf on 27/07/2017.
 */
class ArtUsers {

    static
    def compareUsers (Artifactory master, Artifactory node1, Artifactory node2) {

        List masterUsers = master.security().userNames()
        List node1Users = node1.security().userNames()
        List node2Users = node2.security().userNames()

        if (masterUsers != node1Users) {
            println "Test Failed - Master Users List does not match Node1 Group; Differences: "
            println masterUsers - node1Users
            return false
        }

        if (masterUsers != node2Users) {
            println "Test Failed - Master Users List does not match Node1 Group; Differences: "
            println masterUsers - node2Users
            return false
        }
        return true
    }

    static def verifyNoDefaultUsers (Artifactory art) {
        SecurityTestApi sa = new SecurityTestApi(art)
        List<String> defaultUserList = sa.getDefaultUsersList()
        try {
            defaultUserList.each { defaultUser ->
                art.security().user(defaultUser)
                println "Found a default user ${defaultUser} at: " + art.getUri()
                return false
            }
        } catch (HttpResponseException he) {
           null
        }
        return true
    }

    static def verifyDefaultUsers (Artifactory art) {
        SecurityTestApi sa = new SecurityTestApi(art)
        List<String> defaultUserList = sa.getDefaultUsersList()
        try {
            defaultUserList.each { defaultUser ->
                art.security().user(defaultUser)
            }
        } catch (HttpResponseException he) {
            println "Did not find a default user at: " + art.getUri() + ". Error: " + he.message
            return false
        }
        return true
    }

    static def verifynoReplicateUsers (Artifactory art) {
        SecurityTestApi sa = new SecurityTestApi(art)
        List<String> noDeleteUsersList = ['xray', '_internal', 'access-admin']
        try {
           noDeleteUsersList.each { defaultUser ->
                art.security().user(defaultUser)
            }
        } catch (HttpResponseException he) {
            println "Did not find a do not delete user at: " + art.getUri() + ". Error: " + he.message
            return false
        }
        return true
    }

    static def getUserCount (Artifactory art) {
        SecurityTestApi sa = new SecurityTestApi(art)
        return sa.getAllUsers().size()
    }

    static def createApiKeyDefaultUser (Artifactory art) {
        def userID

        SecurityTestApi sa = new SecurityTestApi(art)
        List<String> defaultUserList = sa.getDefaultUsersList()
        try {
            defaultUserList.each { defaultUser ->
                userID = defaultUser
                sa.createUserApiKey(defaultUser, 'jfrog')
            }
        } catch (HttpResponseException he) {
            println "Cannot create APIKey for user ${userID}: " + art.getUri() + ". Error: " + he.message
            return false
        }
        return true
    }

    static def regenerateApiKeyDefaultUser (Artifactory art) {
        def userID

        SecurityTestApi sa = new SecurityTestApi(art)
        List<String> defaultUserList = sa.getDefaultUsersList()
        try {
            defaultUserList.each { defaultUser ->
                userID = defaultUser
                sa.regenerateUserApiKey(defaultUser, 'jfrog')
            }
        } catch (HttpResponseException he) {
            println "Cannot regenerate APIKey for user ${userID}: " + art.getUri() + ". Error: " + he.message
            return false
        }
        return true
    }

    static def verifyApiKeyDefaultUser (Artifactory master, Artifactory node1, Artifactory node2) {
        def userID
        def apiKey

        SecurityTestApi sa = new SecurityTestApi(master)
        List<String> defaultUserList = sa.getDefaultUsersList()

        // validate all default user have APIKeys
        try {
            defaultUserList.each { defaultUser ->
                userID = defaultUser
                apiKey = sa.getUserApiKey(defaultUser,'jfrog')
            }
        } catch (HttpResponseException he) {
            println "Cannot get APIKey for user ${userID}: " + master.getUri() + ". Error: " + he.message
            return false
        }

        defaultUserList.each { defaultUser ->
            if (!verifyApiKeyonAllNodes(defaultUser, master, node1, node2)) {
                return false
            }
        }
        return true
    }

    static def verifyApiKeyonAllNodes (String userID, Artifactory master, Artifactory node1, Artifactory node2) {
        def masterAPiKey

        Artifactory artMaster = ArtifactoryClientBuilder.create().setUrl(master.getUri()).setUsername(userID).setPassword('jfrog').build()
        SecurityTestApi sa = new SecurityTestApi(artMaster)
        masterAPiKey = sa.getUserApiKey(userID, 'jfrog')

        Artifactory artNode1 = ArtifactoryClientBuilder.create().setUrl(node1.getUri()).setUsername(userID).setPassword('jfrog').build()
        SecurityTestApi n1 = new SecurityTestApi(artNode1)
        if (masterAPiKey != n1.getUserApiKey(userID, 'jfrog')) {
            println "Master fail to replicate ApiKey for user ${userID} to node 1"
            return false
        }

        Artifactory artNode2 = ArtifactoryClientBuilder.create().setUrl(node2.getUri()).setUsername(userID).setPassword('jfrog').build()
        SecurityTestApi n2 = new SecurityTestApi(artNode2)
        if (masterAPiKey != n2.getUserApiKey(userID, 'jfrog')) {
            println "Master fail to replicate ApiKey for user ${userID} to node 2"
            return false
        }
        return true
    }
}
