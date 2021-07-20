## __*** Deprecation NOTICE ***__

*Artifactory 6.6 introduced [Build Info Repositories](https://www.jfrog.com/confluence/display/RTF/Release+Notes#ReleaseNotes-Artifactory6.6).  With this introduction, Replication of build info can be introduced via [push replication](https://www.jfrog.com/confluence/display/RTF/Repository+Replication#RepositoryReplication-PushReplication) configured via the [REST API](https://www.jfrog.com/confluence/display/RTF/Repository+Replication#RepositoryReplication-ReplicatingwithRESTAPI). Note that replication cannot currently be configured via the UI for build info repositories.*

*Documentation and plugin is preserved here for users of older versions.*



Artifactory Build Sync User Plugin
==================================

Build info is a powerful method of tracking metadata associated with artifacts
in Artifactory, and the Build Sync user plugin allows this data to be kept in
sync with all of multiple Artifactory instances with push, pull, and event push
replication.

Reference the User [Plugin documentation](https://www.jfrog.com/confluence/display/JFROG/User+Plugins)
for more information about plugins.

Features
--------

- Keep or delete local build numbers that do not exist on the remote server.
- When replicating, insert build info like it was deployed from the build
  server.
- Pattern matching build name replication.
- Optional turn on/off listening of build save plugins when replication is
  fired.
- Replicate promotion status.

Installation
------------

To install Build Sync:

1. Edit the `${ARTIFACTORY_HOME}/var/etc/artifactory/logback.xml` to add:

   ```xml
   <logger name="buildSync">
       <level value="debug"/>
   </logger>
   ```

1. Download the following dependency jars, and copy them to the
   `${ARTIFACTORY_HOME}/etc/plugins/lib` directory:
   * [HTTPBuilder](https://bintray.com/bintray/jcenter/org.codehaus.groovy.modules.http-builder%3Ahttp-builder/_latestVersion)
   * [Json-lib](https://bintray.com/bintray/jcenter/net.sf.json-lib%3Ajson-lib/_latestVersion)
   * [Xml-resolver](https://bintray.com/bintray/jcenter/xml-resolver%3Axml-resolver/_latestVersion)
1. Edit the `buildSync.json` file. See the example `buildSync.json` provided or
   the details below on how to edit `buildSync.json`.
1. Copy `buildSync.groovy` and `buildSync.json` to the `${ARTIFACTORY_HOME}/etc/artifactory/plugins` directory.
1. There are two ways to load the plugin:
   - Manually force the plugin to be loaded `curl -u username:password -XPOST -H'Content-Type: application/json' http://localhost:8080/artifactory/api/plugins/reload`
   - Refer to the Plugin documentation for how to enable **Auto Reload**.
1. Verify in the `$JF_PRODUCT_DATA_INTERNAL/logs/artifactory-service.log` that the plugin
   loaded the configuration correctly.

Permissions
-----------

To call the plugin execute REST API an **admin** account must be used.

Creating A buildSync.json
-------------------------

1. First list the servers with:

   ```json
   "servers": [
       {
           "key": "local-1",
           "url": "http://localhost:8080/artifactory",
           "user": "admin",
           "password": "password"
       },
       ...
   ]
   ```

   Each server must have a unique key identifying it, and url/user/password used
   to access the REST API.

1. Then list the pull replication configurations with:

   ```json
   "pullConfigs": [
       {
           "key": "AllFrom2",
           "source": "local-2",
           "buildNames": ["*"],
           "delete": true,
           "reinsert": false,
           "activatePlugins": true,
           "syncPromotions": false,
           "maxThreads": 10
       },
       ...
   ]
   ```

   - Each pull configuration should have a unique `key` identifying it.
     (mandatory)
   - The `source` is pointing to one server key and should exists in the above
     list of servers. (mandatory)
   - The `buildNames` are a list of string to filter build names to
     synchronized. (mandatory)
     - If the string contains a `*` star character it is considered wildcard. If
       not the build name should be exactly equal (with case sensitivity). (mandatory)
   - The `delete` flag tells the synchronization to delete all the local build
     numbers that do not exists in the remote server. (Optional, false by
     default)
   - The `reinsert` flag tells the synchronization to fully reinsert the build
     info locally. (Optional, false by default). This will activate all the
     plugins associated with build deployment, including:
     - Change Artifactory deployer to current user.
     - Activate Issues aggregation.
     - Activate Third Party Control, or OSS Governance.
     - Activate all Users Plugins.
   - The `activatePlugins` flag will add the new build info as is and activate
     only the User Plugins. (Optional, false by default)
   - The `syncPromotions` flag will check and synchronize promotion status. (Optional, false by default)
   - The `maxThreads` setting defines the number of threads to use when syncing.  
     The default is 10, set it higher for faster performance on larger servers.

1. Then list the push replication configurations with:

   ```json
   "pushConfigs": [
       {
           "key": "PushTo23",
           "destinations": [ "local-2", "local-3" ],
           "buildNames": ["*"],
           "delete": true,
           "syncPromotions": false,
           "maxThreads": 10,
           "activateOnSave": false
       },
       ...
   ]
   ```

   Everything is the same as pull configurations, except:
   - The `activateOnSave` flag will add a listener in this plugin that will trigger push as soon as a new build arrives. (Optional, false by default)   
   - In Push mode a full reinsert is done on the remote server.

Testing
-------

   To being replication, make a simple REST request.

   - Executing Pull Replication:

     ```
     >>> curl -X POST -v -u admin:password "http://localhost:8080/artifactory/api/plugins/execute/buildSyncPullConfig?params=key=myPullKey"
     Builds [*] from http://localhost:8080/artifactory/ successfully replicated:
     my-build-name-1: already-synched
     my-build-name-2:7:3:3
     ```

     **Note** There are two optional params for pull replication
     ```
     curl -X POST -v -u admin:password "http://localhost:8080/artifactory/api/plugins/execute/buildSyncPullConfig?params=key=ABRelease|max=N|force=0"
     ```
        - max parameter defined a reversing order sync max number
        - force parameter will not stop sync if latest build match

   - Executing Push Replication:

     ```
     >>>  curl -X POST -v -u admin:password "http://localhost:8080/artifactory/api/plugins/execute/buildSyncPushConfig?params=key=myPushKey"
     (need example output)
     ```
