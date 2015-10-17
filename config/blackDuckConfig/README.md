Artifactory Black Duck Config User Plugin
=========================================

Allows REST access to the Black Duck configuration. This plugin exposes two
executions:

getBlackDuck
------------

`getBlackDuck` returns a JSON representation of the current Black Duck
configuration. The returned JSON string has the following fields:

- `enableIntegration`: Whether Black Duck is enabled.
- `serverUri`: The URI of the Black Duck server.
- `username`: The username with which to log in.
- `password`: The password with which to log in.
- `connectionTimeoutMillis`: The network timeout to use.
- `proxy`: The key of the proxy configuration to use.

For example:

```
$ curl -u admin:password 'http://localhost:8081/artifactory/api/plugins/execute/getBlackDuck'
{
    "enableIntegration": true,
    "serverUri": "http://somehost",
    "username": "admin",
    "password": "password",
    "connectionTimeoutMillis": 5000,
    "proxy": null
}
```

setBlackDuck
------------

`setBlackDuck` updates the current Black Duck configuration using the provided
JSON object. This object may have any combination of the fields listed above,
and only the provided fields will be updated in the configuration.

For example:

```
$ curl -u admin:password -X POST -H 'Content-Type: application/json' -d '{
> "enableIntegration": true,
> "serverUri": "http://somehost",
> "username": "admin",
> "password": "password",
> "connectionTimeoutMillis": 20000,
> "proxy": null
> }' 'http://localhost:8081/artifactory/api/plugins/execute/setBlackDuck'
```
