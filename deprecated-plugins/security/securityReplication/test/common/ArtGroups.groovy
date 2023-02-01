package common

import artifactory.SecurityTestApi
import org.apache.http.client.HttpResponseException
import org.jfrog.artifactory.client.Artifactory


/**
 * Created by stanleyf on 27/07/2017.
 */
class ArtGroups {

    static def compareGroups (Artifactory master, Artifactory node1, Artifactory node2) {

        List masterGroups = master.security().groupNames()
        List node1Groups = node1.security().groupNames()
        List node2Groups = node2.security().groupNames()

        if (masterGroups != node1Groups) {
            println "Test Failed - Master Group does not match Node1 Group; Differences: "
            println masterGroups - node1Groups
            return false
        }

        if (masterGroups != node2Groups) {
            println "Test Failed - Master Group does not match Node1 Group; Differences: "
            println masterGroups - node2Groups
            return false
        }
        return true
    }

    static def verifyNoDefaultGroups (Artifactory art) {
        SecurityTestApi sa = new SecurityTestApi(art)
        List<String> defaultGroupList = sa.getDefaultGroupsList()
        boolean found = false
        try {
            defaultGroupList.each { defaultGroup ->
                art.security().group(defaultGroup)
                println "Found a default group ${defaultGroup} at: " + art.getUri()
                found = true
            }
        } catch (HttpResponseException he) {
            null
        }
        if (found) return false
        return true
    }

    static def verifyDefaultGroups (Artifactory art) {
        SecurityTestApi sa = new SecurityTestApi(art)
        List<String> defaultGroupList = sa.getDefaultGroupsList()
        try {
            defaultGroupList.each { defaultGroup ->
                art.security().group(defaultGroup)
            }
        } catch (HttpResponseException he) {
            println "Found a default group at: " + art.getUri() + ". Error: " + he.message
            return false
        }
        return true
    }

    static def getGroupCount (Artifactory art) {
        SecurityTestApi sa = new SecurityTestApi(art)
        return sa.getGroupsList().size()
    }
}
