Artifactory SMTP Config User Plugin
===================================

Allows REST access to the SMTP configuration. This plugin exposes two
executions:

getSmtp
-------

`getSmtp` returns a JSON representation of the current SMTP configuration. The
returned JSON string has the following fields:

- `enabled`: Whether SMTP is enabled.
- `host`: The host name of the mail server.
- `port`: The port to use for the mail server.
- `username`: The username for the mail server.
- `password`: The password for the mail server.
- `from`: The source address to use for outgoing emails.
- `subjectPrefix`: A prefix to add to outgoing emails.
- `tls`: Whether to use tls.
- `ssl`: Whether to use ssl.
- `artifactoryUrl`: The url to use for hyperlinks to Artifactory in outgoing
  emails.

For example:

```
$ curl -u admin:password 'http://localhost:8081/artifactory/api/plugins/execute/getSmtp'
{
    "enabled": true,
    "host": "somehost",
    "port": 25,
    "username": "admin",
    "password": "password",
    "from": null,
    "subjectPrefix": "[Artifactory]",
    "tls": false,
    "ssl": false,
    "artifactoryUrl": null
}
```

setSmtp
-------

`setSmtp` updates the current SMTP configuration using the provided JSON object.
This object may have any combination of the fields listed above, and only the
provided fields will be updated in the configuration.

For example:

```
$ curl -u admin:password -X POST -H 'Content-Type: application/json' -d '{
> "enabled": true,
> "host": "somehost",
> "port": 25,
> "username": "admin",
> "password": "password",
> "from": null,
> "subjectPrefix": "[Artifactory]",
> "tls": false,
> "ssl": false,
> "artifactoryUrl": null
> }' 'http://localhost:8081/artifactory/api/plugins/execute/setSmtp'
```
