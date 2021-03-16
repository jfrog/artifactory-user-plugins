Artifactory Authenticate Entitlements User Plugin
=================================================

*Please Note:  This plugin is still in the PROTOTYPE phase.  Please contact JFrog Support before using it.*

This plugin authenticates client IP address and entitlements by
injecting the approval of an external REST service into the authentication
process using **altResponse**.

Reference the User [Plugin documentation](https://www.jfrog.com/confluence/display/JFROG/User+Plugins)
for more information about plugins.

Features
--------

- Calls external REST service for additional authentication beyond just
  username/password. For example: Client IP address verification.
- Provides two examples of chained authentication for IP address and
  user access to specific artifacts.
- Provides simulated REST service example.

Installation
---------------------

1. Edit the `${ARTIFACTORY_HOME}/var/etc/artifactory/logback.xml` to add:

   ```xml
   <logger name="approveDeny">
       <level value="debug"/>
   </logger>
   ```

1. Download the following dependency jars, and copy them to the
  `${ARTIFACTORY_HOME}/var/etc/artifactory/plugins/lib` directory:
  * [HTTPBuilder](https://mvnrepository.com/artifact/org.codehaus.groovy.modules.http-builder/http-builder/0.7.2)
  * [Json-lib](https://mvnrepository.com/artifact/net.sf.json-lib/json-lib/2.4)
  * [Xml-resolver](https://mvnrepository.com/artifact/xml-resolver/xml-resolver/1.2)
1. Edit the `approveDeny.json` file. See the example `approveDeny.json` provided or
   the details below on how to edit `approveDeny.json`.
1. Copy `approveDeny.groovy` and `approveDeny.json` to the `${ARTIFACTORY_HOME}/var/etc/artifactory/artifactory/plugins` directory.
1. There are two ways to load the plugin:
   - Manually force the plugin to be loaded `curl -u username:password -XPOST -H'Content-Type: application/json' http://localhost:8082/artifactory/api/plugins/reload`
   - Refer to the Plugin documentation for how to enable **Auto Reload**.
1. Verify in the `$JF_PRODUCT_DATA_INTERNAL/logs/artifactory-service.log` that the plugin
   loaded the configuration correctly.

**NOTE**: We recommend you to setup a `.netrc` file (see https://everything.curl.dev/usingcurl/netrc) to store your username and password for `curl`, e.g. `curl -n`, `curl --netrc`, `curl --netrc-file`.

Creating an approveDeny.json
-------------------------

1. There is support for two servers: IP validation and entitlement validation. Adjust
   URL (the current version is a test server running locally on port 8888):

  ```
  {
    "ipServer": "http://host.docker.internal:8888/validateIP",
    "entitlementServer": "http://host.docker.internal:8888/validateEntitlements"
  }
  ```

REST API
-------------------------
1. IP Server:

  Expects to receive the json object:

  ```
  {'Transaction_IP': '', 'AppName': '', 'Email': ''}
  ```

  Expects the result:

  ```
  {'status': 'Approved/DECLINE', 'country': '', 'country2letter': '', 'domain': '', 'region': '', 'message': ''}
  ```

1. Entitlement Server:

  Expects to receive the json object:

  ```
  {'RepositoryName': '', 'ModuleName': '', 'ProductNameSpace': '', 'Version': '', 'Email': ''}
  ```

  Expects the result:

  ```
  {'status': 'Approved/DECLINE'}
  ```

Custom Approve Deny REST Endpoints
==================================

The Approve Deny user plugin caches the responses from the IP and the Entitlement
servers. The cache will grow and each cached item will become stale after
10 minutes but the entire cache is cleared manually. For that there are REST APIs

1. The **getcachesize** API is to find out how many elements are in the cache:
  ```
  curl -u admin:password -XPOST -H'Content-Type: application/json' "http://localhost:8082/artifactory/api/plugins/execute/getcachesize"
  ```

  The return JSON is of the following format:

  ```
  {"CacheSize":"0"}
  ```

1. The **purgecache** API will purge the entire cache marking all of the items
for garbage collection:

  ```
  curl -u admin:password -XPOST -H'Content-Type: application/json' "http://localhost:8082/artifactory/api/plugins/execute/purgecache"
  ```

Testing
-------

Please see [DEBUG.md](DEBUG.md) for testing and debugging.
