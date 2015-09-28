Artifactory HTTP SSO Config User Plugin
=======================================

Allows REST access to the HTTP SSO configuration. This plugin exposes two
executions:

getHttpSso
----------

`getHttpSso` returns a JSON representation of the current HTTP SSO
configuration. The returned JSON string has the following fields:

- `httpSsoProxied`: Whether Artifactory is proxied by a secure HTTP server.
- `noAutoUserCreation`: Whether users who log in via HTTP SSO are created
  automatically by Artifactory.
- `remoteUserRequestVariable`: The name of the HTTP request variable to use to
  extract the user identity.

For example:

```
$ curl -u admin:password 'http://localhost:8081/artifactory/api/plugins/execute/getHttpSso'
{
    "httpSsoProxied": false,
    "noAutoUserCreation": false,
    "remoteUserRequestVariable": "REMOTE_USER"
}
```

setHttpSso
----------

`setHttpSso` updates the current HTTP SSO configuration using the provided JSON
object. This object may have any combination of the fields listed above, and
only the provided fields will be updated in the configuration.

For example:

```
$ curl -u admin:password -X POST -H 'Content-Type: application/json' -d '{
> "httpSsoProxied": false,
> "noAutoUserCreation": true,
> "remoteUserRequestVariable": "REMOTE_USER"
> }' 'http://localhost:8081/artifactory/api/plugins/execute/setHttpSso'
```
