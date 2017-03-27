Artifactory Storage Summary User Plugin
=======================================

Exposes a summary of the storage info via the REST api.

Usage
-----

This plugin exposes the set of executions which redirect an API call to the
specified node in HA cluster. The returned JSON string has the same fields
as the target endpoint.

If the `serverId` parameter is optional. If it is not specified,
the request is not redirected, i.e. it's handled by current server.

For example:

```
$ curl -u admin:password \
 'http://localhost:8081/artifactory/api/plugins/execute/getSystemInfo?params=serverId=ha_artifactory_1_2'
```

There is also generic execution, `routedGet` allowing to redirect arbitrary API call
to a specific cluster node, however on an instance where the HA feature
is not configured, only the APIs which have corresponding non-generic execution
are supported (see the plugin code for the list of non-generic executions).

For the generic execution, the `apiEndpoint` parameter id requires.

For example:

```
$ curl -u admin:password \
 'http://localhost:8081/artifactory/api/plugins/execute/routedGet?params=serverId=ha_artifactory_1_1|apiEndpoint=api/plugins/execute/haClusterDump'
```


Update licence for node
-----
Updates licence for specified node in HA cluster.

The `serverId` parameter is optional. If it is not specified,
the request is not redirected, i.e. it's handled by current server.

Usage:

```
$ curl -X POST -u admin:password \
 -d '{"licenseKey":"licence key here"}' 'http://localhost:8081/artifactory/api/plugins/execute/updateLicense?params=serverId=ha_artifactory_1_2'
```