Artifactory Proxies Config User Plugin
======================================

Allows REST access to the proxy configuration. This plugin exposes five
executions:

getProxiesList
--------------

`getProxiesList` returns a JSON list containing the keys of all the currently
configured proxies.

For example:

```
$ curl -u admin:password 'http://localhost:8081/artifactory/api/plugins/execute/getProxiesList'
[
    "newproxy"
]
```

getProxy
--------

`getProxy` returns a JSON representation of the current configuration of a given
proxy. The key of the requested proxy is passed as the parameter `key`. The
returned JSON string has the following fields:

- `key`: The proxy's unique ID key.
- `host`: The host name of the proxy.
- `port`: The port number of the proxy.
- `username`: The username to use.
- `password`: The password to use.
- `ntHost`: The NTLM hostname of this machine.
- `domain`: The domain/realm name.
- `defaultProxy`: Whether this proxy is the default.
- `redirectedToHosts`: An optional list of host names (separated by newline or comma) to which the proxy may redirect requests. The credentials of the proxy are reused by requests redirected to any of these hosts.

For example:

```
$ curl -u admin:password 'http://localhost:8081/artifactory/api/plugins/execute/getProxy?params=key=newproxy'
{
    "key": "newproxy",
    "host": "somehost",
    "port": 8080,
    "username": "user",
    "password": "pass",
    "ntHost": null,
    "domain": null,
    "defaultProxy": false,
    "redirectedToHosts": "host1,host2"
}
```

deleteProxy
-----------

`deleteProxy` deletes a proxy from the Artifactory instance. The key of the
proxy to delete is passed as the parameter `key`.

For example:

```
$ curl -u admin:password -X DELETE 'http://localhost:8081/artifactory/api/plugins/execute/deleteProxy?params=key=newproxy'
```

addProxy
--------

`addProxy` adds a new proxy to the Artifactory instance. The proxy to add is
defined by a JSON object sent in the request body, with the same schema used by
`getProxy`.

For example:

```
$ curl -u admin:password -X POST -H 'Content-Type: application/json' -d '{
> "key": "newproxy",
> "host": "somehost",
> "port": 8080,
> "username": "user",
> "password": "pass",
> "ntHost": null,
> "domain": null,
> "defaultProxy": false,
> "redirectedToHosts": "host1,host2"
> }' 'http://localhost:8081/artifactory/api/plugins/execute/addProxy'
```

updateProxy
-----------

`updateProxy` updates an existing proxy. The key of the proxy to modify is
passed as the parameter `key`. The modifications are defined by a JSON object
sent in the request body, with the same schema used by `getProxy` and
`addProxy`. Only the fields that should be modified need to be included in the
JSON representation, and all other fields will be preserved.

For example:

```
$ curl -u admin:password -X POST -H 'Content-Type: application/json' -d '{
> "host": "someotherhost",
> "port": 8085,
> "defaultProxy": true
> }' 'http://localhost:8081/artifactory/api/plugins/execute/updateProxy?params=key=newproxy'
```
