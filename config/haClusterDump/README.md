Artifactory HA Cluster Dump User Plugin
=======================================

Allows dumping of HA cluster data via the REST api.

Usage
-----

This plugin exposes the execution `haClusterDump`, which returns a JSON
representation of the dump. The returned JSON string has the following fields:

- `active`: Whether HA is enabled and active on this server.
- `members`: A list of objects, each representing a member of the HA cluster.

Each member has the following fields:

- `serverId`: The server's unique ID.
- `localMember`: Whether this server is the one being queried by this REST call.
- `address`: The server's internet address.
- `heartbeat`: The date and time of the last heartbeat, in ISO 8601 format.
- `serverState`: The current state of the server. Value can be `"UNKNOWN"`,
  `"OFFLINE"`, `"STARTING"`, `"RUNNING"`, `"STOPPING"`, `"STOPPED"`, or
  `"CONVERTING"`.
- `serverRole`: Server role. Value can be `"Standalone"`, `"Primary"`, `"Member"`, `"Copy"`
- `artifactoryVersion`: server Artifactory version


For example:

```
$ curl -u admin:password 'http://localhost:8081/artifactory/api/plugins/execute/haClusterDump'
```
