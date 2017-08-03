Artifactory Build Replication User Plugin
=======================================

Build Replication plugin replicates all build info json from master Artifactory to slave Artifactory instance.

Installation
---------------------------------------
To install Build Replication:

1. Place buildReplication.groovy under the master Artifactory server `${ARTIFACTORY_HOME}/etc/plugins`.
2. Place buildReplication.properties file under `${ARTIFACTORY_HOME}/etc/plugins`.


Execution
---------------------------------------

1. The plugin may run using the `buildReplication.properties`
2. The file should contain:

	``` 
	master=http://localhost:8080/artifactory
	masterUser=admin
	masterPassword=password
	slave=http://localhost:8081/artifactory
	slaveUser=admin
	slavePassword=password
	deleteDifferentBuild=true/false
	```
3. Execution
	- Call the plugin execution by running:
	```
	curl -X POST -v -u admin:password "http://localhost:8080/artifactory/api/plugins/execute/buildReplication"
	```
	- You can pass all the parameters for the plugin in the REST call:
	```
	curl -X POST -v -u admin:password "http://localhost:8080/artifactory/api/plugins/execute/buildReplication?params=master=http://localhost:8080/artifactory;masterUser=admin;masterPassword=password;slave=http://localhost:8081/artifactory;slaveUser=admin;slavePassword=password;deleteDifferentBuild=false"
	```

Logic
---------------------------------------
1. The plugin compare the list of build from master and slave and:
	- Add all builds of projects that are found on master but not on slave.
	- Projects that are found on slave but not on master are completely ignored.
2. If `deleteDifferentBuild=false`:
	- Builds deleted from master and already exists in slave will not be deleted.
3. BE CAREFUL `deleteDifferentBuild=true`:
	- Builds deleted from master and already exists in slave will be deleted from slave.
	- If the delete artifacts flag is on inside the build info it will also delete artifacts on the slave.
The script can get 7 params:
	- Artifactory main server name (the replication will be from this server to the replicated one)
	- User name and password\security key to the main server
	- Artifactory replicated server
	- User name and password\security key to the replicated server
	- True\false flag which delete builds that don't exist at the main server and exist at the replicated server (not mandatory)
	```
	curl -X POST -v -u admin:password "http://localhost:8080/artifactory/api/plugins/execute/buildReplication?params=master=masterArtifactory;masterUser=masterArtifactoryUser;masterPassword=masterPassword;slave=slaveArtifactory;slaveUser=slaveUser;slavePassword=slavePassword;deleteDifferentBuild=true/false"
	```
