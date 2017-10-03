package definitions

class Constants {
	public static final String USERID = "admin";

	public static final String URL = "http://104.198.193.76:8081/artifactory/";
	public static final String PASSWORD = "password"

	public static final String SYSTEM_DECRYPT = "api/system/decrypt"
	public static final String SYSTEM_ENCRYPT = "api/system/encrypt"
	public static final String ARTIFACTORY_PING = "api/system/ping"
	public static final String ARTIFACTORY_PING_FAIL = "artifactory ping failed"
	public static final String ARTIFACTORY_STATUS_EXCEPTION = "caught an exception artifactory ping"

	public static final String SECURITY_ERR = "Security API error"
	public static final String SECURITY_EXCEPTION = "caught exception with security api"
	public static final String SECURITY_USER_API = "api/security/users"
	public static final String SECURITY_USER_CONTENT_TYPE = "Content-Type:application/vnd.org.jfrog.artifactory.security.User+json"
	public static final String SECURITY_GROUP_API = "api/security/groups"
	public static final String SECURITY_GROUP_REALM ="Docker Trusted Registry"
	public static final String SECURITY_PERMISSION_API = "api/security/permissions"
	public static final String SECURITY_DELETEALL_APIKEY_API = "api/security/apiKey?deleteAll=1"
	public static final String TASK_LIST_API = "api/tasks"

	public static final String PLUGIN_EXECUTE_SECREP_JSON = 'api/plugins/execute/secRepJson'
	public static final String REPOSITORIES_GET_API_LOCAL = "api/repositories?type=local"
	public static final String REPOSITORIES_GET_API_REMOTE = "api/repositories?type=remote"
	public static final String REPOSITORIES_GET_API_VIRTUAL = "api/repositories?type=virtual"
	public static final String REPOSITORIES_PUT_API = "api/repositories/"
	public static final String REPOSITORIES_DELETE_API = "api/repositories/"
	public static final String REPOSITORIES_GET_CONTENT_TYPE = "Content-Type:application/vnd.org.jfrog.artifactory.repositories.RepositoryDetailsList+json"
	public static final String REPOSITORIES_CREATE_CONTENT_TYPE = "Content-Type:application/vnd.org.jfrog.artifactory.repositories.LocalRepositoryConfiguration+json"
	public static final String REPOSITORIES_PUT_DIRECTORY = "/artifactory/"
	public static final String REPOSITORIES_DIRECTORY_CONTENT_TYPE = "Content-Type:application/vnd.org.jfrog.artifactory.storage.ItemCreated+json"
	public static final String REPOSITORIES_PUSH_ERR = "Failed to push image to artifactory"
	public static final String REPOSITORIES_DOCKER_LOGIN_ERR = "Docker login failed"
	public static final String SECURITY_REPLICATION_DESC = "User Plugin Job - securityReplicationWorker"

	public static final String contentTypeRepoLocal = "Content-Type:application/vnd.org.jfrog.artifactory.repositories.LocalRepositoryConfiguration+json"
	public static final String contentTypeRepoVirtual = "Content-Type:application/vnd.org.jfrog.artifactory.repositories.VirtualRepositoryConfiguration+json"
	public static final String contentTypeRepoRemote = "Content-Type:application/vnd.org.jfrog.artifactory.repositories.RemoteRepositoryConfiguration+json"
	public static final String contentTypeSecurityGroup = "Content-Type:application/vnd.org.jfrog.artifactory.security.Group+json"
	public static final String contentTypeSecurityUser = "Content-Type:application/vnd.org.jfrog.artifactory.security.User+json"
	public static final String contentTypeSecurityUsers = "Content-Type:application/vnd.org.jfrog.artifactory.security.Users+json"
	public static final String contentTypeReplication = "Content-Type:application/vnd.org.jfrog.artifactory.replications.ReplicationConfigRequest+json"
	public static final String contentTypeMultiPushRepl = "Content-Type:application/vnd.org.jfrog.artifactory.replications.MultipleReplicationConfigRequest+json"
	public static final String contentTypeSecurityPermission = "Content-Type:application/vnd.org.jfrog.artifactory.security.PermissionTarget+json"
	public static final String contentTypeApplicationText = "Content-Type:application/text"
	public static final String contentTypeApplicationJSON = "Content-Type:application/json"
}

