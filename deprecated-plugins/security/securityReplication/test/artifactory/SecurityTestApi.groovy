package artifactory

import data.Groups
import data.PermissionTargets
import data.Users
import definitions.GroupClass
import definitions.UserClass
import definitions.Constants
import org.jfrog.artifactory.client.Artifactory
import org.jfrog.artifactory.client.ArtifactoryClientBuilder
import org.jfrog.artifactory.client.ArtifactoryRequest
import org.jfrog.artifactory.client.impl.ArtifactoryRequestImpl
import org.jfrog.artifactory.client.model.builder.PrincipalsBuilder
import org.jfrog.artifactory.client.model.builder.UserBuilder
import org.jfrog.artifactory.client.model.User
import org.jfrog.artifactory.client.model.Group
import org.jfrog.artifactory.client.model.Principal
import org.jfrog.artifactory.client.model.Principals
import org.jfrog.artifactory.client.model.PermissionTarget
import org.apache.http.client.HttpResponseException

/**
 * Created by stanleyfong on 8/18/16.
 */
class SecurityTestApi {

    final static CREATE_TIMER = 20
    Users uList = new Users()
    Artifactory art

    List<Principal> userPList = []
    List<Principal> groupPList = []
    List<String> repoPList = []
    Collection<String> userGroupList = []

    SecurityTestApi(Artifactory art ) {
        this.art = art;
    }

//######################################################################
//######## USERS            ############################################
//######################################################################

    def createDefaultUserList () {
        uList.clearUserList()
        uList.createUsersList()
        uList.userList.each { user ->
            createUser (user)
        }
    }

    def createSingleUser (String userName, String password) {
        UserClass user = new UserClass ()
        user.name = userName
        user.password = password
        user.email = 'null@jfrog.com'
        createUser(user)
    }

    def createDynamicUserList (int count, String userPrefix, int startIndex, def password) {
        uList.clearUserList()
        uList.createDynamicUserList(count, startIndex, userPrefix, password )
        uList.userList.each { user ->
            createUser (user)
        }
    }

    def createUser (UserClass user) {
        buildGroupList (user.groups)
        UserBuilder ub = art.security().builders().userBuilder()
        User userId = ub.name(user.name)
                .email(user.email)
                .admin(user.admin)
                .groups(userGroupList)
                .profileUpdatable(user.profileUpdatable)
                .password(user.password)
                .build()
        art.security().createOrUpdate(userId)
        sleep (CREATE_TIMER)
    }


    def deleteDefaultUser () {
        uList.clearUserList()
        uList.createUsersList()
        uList.userList.each { user ->
            art.security().deleteUser(user.name)
        }
    }

    def deleteAllUsers () {
        Collection<String> userNames = art.security().userNames()
        for (String userName : userNames) {
            if (userName != "anonymous" && userName != "admin" && userName != "access-admin" && userName != "_internal" && userName != "xray") {
                art.security().deleteUser(userName)
            }
        }
    }

    def Collection<String> getAllUsers () {
        return art.security().userNames()
    }

    def deleteUser (String userId) {
        art.security().deleteUser(userId)
    }

    List<String> getDefaultUsersList () {
        List<String> defaultUserList = []
        uList.clearUserList()
        uList.createUsersList()
        uList.userList.each { user ->
            defaultUserList << user.name
        }
        return defaultUserList
    }

    def revokeAllApiKeys () {
        ArtifactoryRequest securityRequest = new ArtifactoryRequestImpl().apiUrl("api/security/apiKey")
            .method (ArtifactoryRequest.Method.DELETE)
            .addQueryParam("deleteAll", "1")
            .responseType(ArtifactoryRequest.ContentType.JSON)
        art.restCall(securityRequest)
    }

    def revokeUserApiKey (def userName) {
        ArtifactoryRequest securityRequest = new ArtifactoryRequestImpl().apiUrl("api/security/apiKey/${userName}")
            .method (ArtifactoryRequest.Method.DELETE)
        art.restCall(securityRequest)
    }

    def getUserApiKey (String userName, String password) {
        Artifactory artifactory = ArtifactoryClientBuilder.create().setUrl(art.getUri() + "/artifactory").setUsername(userName).setPassword(password).build()
        ArtifactoryRequest securityRequest = new ArtifactoryRequestImpl().apiUrl("api/security/apiKey")
            .method (ArtifactoryRequest.Method.GET)
            .responseType(ArtifactoryRequest.ContentType.JSON)
        def response = new groovy.json.JsonSlurper().parseText( artifactory.restCall(securityRequest).getRawBody())
        return response['apiKey']
    }

    def regenerateUserApiKey (String userName, String password) {
        Artifactory artifactory = ArtifactoryClientBuilder.create().setUrl(art.getUri() + "/artifactory").setUsername(userName).setPassword(password).build()
        ArtifactoryRequest securityRequest = new ArtifactoryRequestImpl().apiUrl("api/security/apiKey")
                .method (ArtifactoryRequest.Method.PUT)
                .responseType(ArtifactoryRequest.ContentType.JSON)
        def response = new groovy.json.JsonSlurper().parseText( artifactory.restCall(securityRequest).getRawBody())
        return response['apiKey']
    }

    def createUserApiKey (String userName, String password) {
        Artifactory artifactory = ArtifactoryClientBuilder.create().setUrl(art.getUri() + "/artifactory").setUsername(userName).setPassword(password).build()
        ArtifactoryRequest securityRequest = new ArtifactoryRequestImpl().apiUrl("api/security/apiKey")
                .method (ArtifactoryRequest.Method.POST)
                .responseType(ArtifactoryRequest.ContentType.JSON)
        def response = new groovy.json.JsonSlurper().parseText( artifactory.restCall(securityRequest).getRawBody())
        return response['apiKey']
    }

//######################################################################
//######## Groups           ############################################
//######################################################################

    def createDefaultGroups () {
        Groups defaultGroups = new Groups ()
        defaultGroups.defaultGroupList.each { group ->
            createGroup(group)
        }
    }

    def createDynamicGroupList (int count, String userPrefix, int startIndex, def password) {
        Groups gList = new Groups()
        gList.clearGroupsList()
        gList.createDynamicGroupList(count, startIndex, userPrefix, password )
        gList.groupList.each { group ->
            createGroup(group)
        }
    }

    def createGroup (GroupClass group) {
        Group grp = art.security().builders().groupBuilder()
                .name(group.name)
                .autoJoin(group.autoJoin)
                .description(group.description)
                .build()
        art.security().createOrUpdateGroup(grp)
        sleep (CREATE_TIMER)
    }

    def deleteDefaultGroup () {
        Groups defaultGroups = new Groups()
        defaultGroups.defaultGroupList.each { group ->
            println "Deleting " + group.name
            art.security().deleteGroup(group.name)
        }
    }

    def deleteAllGroups () {
        List<String> groupNames = art.security().groupNames()
        for (String groupName : groupNames) {
            art.security().deleteGroup(groupName)
        }
    }

    def List<String> getGroupsList () {
        return art.security().groupNames()
    }

    def List<String> getDefaultGroupsList () {
        List<String> defaultGList = []
        Groups defaultGroups = new Groups ()
        defaultGroups.defaultGroupList.each {group ->
            defaultGList << group.name
        }
        return defaultGList
    }


//######################################################################
//######## Permission       ############################################
//######################################################################

    def deleteDefaultPermission ( ) {
        PermissionTargets permissions = new PermissionTargets();

        permissions.permissionList.each { pItem ->
            try {
                art.security().deletePermissionTarget(pItem.name)
            } catch (HttpResponseException hre) {
                if (hre.statusCode != 404) throw hre
                System.out.println("Could not delete permission with status ${hre.getStatusCode()}, ${hre.localizedMessage} ")
            }
        }
    }

    def createPermissionDefault () {
        PermissionTargets permissions = new PermissionTargets()

        permissions.permissionList.each { pItem ->
            buildUserPermission(pItem.users)
            buildGroupPermission(pItem.groups)
            buildRepositoryPermission(pItem.repositories)

            Principals principals = art.security().builders().principalsBuilder()
                .users(*userPList)
                .groups(*groupPList)
                .build()

            PermissionTarget permissionTarget = art.security().builders().permissionTargetBuilder()
                    .name(pItem.name)
                    .principals(principals)
                    .repositories(*repoPList)
                    .includesPattern(pItem.includesPattern)
                    .excludesPattern(pItem.excludesPattern)
                    .build()
            try {
                art.security().createOrReplacePermissionTarget(permissionTarget)
            } catch (HttpResponseException hre) {
                System.out.println("Could not create permission with status ${hre.getStatusCode()}, ${hre.localizedMessage} ")
            }
        }
    }

    def List<String> getPermissionList () {
        return art.security().permissionTargets()
    }

    def buildUserPermission(Map<String, List> users) {
        userPList.clear()
        users.each { k, v ->
            Principal userP = art.security().builders().principalBuilder()
                    .name(k)
                    .privileges(*v)
                    .build()
            userPList.add(userP)
        }
    }

    def buildGroupPermission(Map<String, List> groups) {
        groupPList.clear()
        groups.each { k, v ->
            Principal groupP = art.security().builders().principalBuilder()
                    .name(k)
                    .privileges(*v)
                    .build()
            groupPList.add(groupP)
        }
    }

    def buildRepositoryPermission (def repoListofList) {
        repoPList.clear()
        repoListofList.each { repo ->
            repoPList << repo.toString()
        }
    }

    def buildGroupList (def groups) {
        userGroupList.clear()
        groups.each { grp ->
            userGroupList.add(grp as String)
        }
    }
}
