import com.sun.org.apache.xerces.internal.impl.dv.util.Base64
import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import java.nio.charset.Charset

/**
 * Created by Alexei Vainshtein on 7/21/15.
 * This plugin finds all the users that are included in a specific group
 */
class IncludedUser {

    String name;

    IncludedUser(String name) {
        this.name = name
    }
}
/**
 * The command to get the info:
 * curl -X GET "http://{ARTIFACTORY_URL}:{PORT}/artifactory/api/plugins/execute/findIncludedUsers?params=group={group_name}" -u{admin_user}:{password}
 * for example: curl -X GET "http://localhost:8081/artifactory/api/plugins/execute/findIncludedUsers?params=group=readers" -uadmin:password
 */
executions {

    findIncludedUsers(httpMethod: 'GET'){ params ->

        //Change the URL to your server url
        String urlString = "http://localhost:8081/artifactory/api/security/users";

        //retriving the user that performs the request. Most be an admin user.
        String user = security.getCurrentUsername();
        String pass = security.getEncryptedPassword();
        ArrayList<String> groupNameArrayFromParams = params['group']
        String groupName = groupNameArrayFromParams.get(0)
        log.info("Getting the required users for the group $groupName")

        //open the connection to the URL and retrieve the json from the call
        def allUsersJson = getJson(urlString,user,pass);
        boolean namesOfGroups;
        ArrayList<String> names = allUsersJson.name
        ArrayList<String> uri = allUsersJson.uri
        def json = new JsonBuilder();

        int i = 0;

        ArrayList<IncludedUser> list = new ArrayList<>();
        while (i < uri.size()) {
            // get if the user is included or not.
            namesOfGroups = getUsersInfo(uri.get(i),groupName, user,pass)
            //if included add the required info to the list as new object IncludedUser (in the basic plugin the info is the user name)
            if (namesOfGroups) {
                list.add(new IncludedUser(names[i]))
            }
            i++
        }
        //creating the json
        json(groupName, list)

        //printing the message to the requested user.
        message  = json.toPrettyString()
        log.info("Finished retrieving the required users for the group $groupName")

    }
}

/**
 * The method returns true if the user is included in the group.
 * true = the user is included in the group. false = not included.
 * @param uri = the URL to retrieve the user information as json (see in Artifactory Wiki REST API Get User Details
 * @param group = the group name that we are checking if the user is included in.
 * @param user = the user that made the request (this is needed to retrieve the json)
 * @param pass = the user password that made the request (this is needed to retrieve the json)
 */

private boolean getUsersInfo(String uri, String group, String user, String pass) {

    def users = getJson(uri,user,pass);
    ArrayList<String> usersGroups = users.groups
    if (usersGroups != null) {
        ArrayList<String> groups = usersGroups.value

        for (int i = 0; i < groups.size(); i++) {
            if (group.equalsIgnoreCase(groups.get(i).toString())) {
                return true;
            }
        }
    }
    return false;
}

/**
 * This method performs the REST call to the required URL and returns the answer as an JSON object
 * @param urlString - the URL to do the REST call
 * @param user - user for authentication
 * @param pass - password for the authentication
 * @return - return the json object of the result.
 */
private Object getJson(String urlString, String user, String pass){
    URL url = new URL(urlString);
    URLConnection uc = url.openConnection();
    String userpass = user + ":" + pass;
    String basicAuth = "Basic " + new String(new Base64().encode(userpass.getBytes()));
    uc.setRequestProperty("Authorization", basicAuth);
    InputStream is = uc.getInputStream();
    def json = new JsonSlurper().parse(new InputStreamReader(is, Charset.forName("UTF-8")));
    //close the connection since we are done using it.
    is.close()
    return json;
}