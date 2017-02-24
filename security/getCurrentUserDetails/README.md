Artifactory Get Current User Details User Plugin
================================================

*This plugin is currently only working in Artifactory 4.x. We are working on updating it to work in Artifactory 5.x.*

This plugin allows a user to retrieve their own detailed user information via a
REST call. This information includes the user's public and private keys, Bintray
authentication info, and user properties, on top of all the data provided by the
[get user details][] REST endpoint.

This plugin is a dependency of the [delegateAuthenticationRealm][] user plugin.

Installation
------------

To install this plugin, place the `getCurrentUserDetails.groovy` file in your
`${ARTIFACTORY_HOME}/etc/plugins` directory.

Usage
-----

This plugin exposes the execution `getCurrentUserDetails`, which returns a JSON
representation of the user details. For example:

```
$ curl -u developer:password 'http://localhost:8081/artifactory/api/plugins/execute/getCurrentUserDetails'
{
    "lastLoginClientIp": "0:0:0:0:0:0:0:1",
    "transientUser": false,
    "privateKey": "...Lo4M9...",
    "username": "developer",
    "anonymous": false,
    "lastLoginTimeMillis": 1472672914756,
    "bintrayAuth": null,
    "realm": "internal",
    "publicKey": "...aPJUt...",
    "updatableProfile": true,
    "groups": [
        "readers"
    ],
    "userProperties": {
        "sshPublicKey": "ssh-rsa ...kE3pD...",
        "passwordCreated": "1472672902316",
        "apiKey": "...X7UAG..."
    },
    "enabled": true,
    "email": "developer@company.net",
    "admin": false
}
```

[get user details]: https://www.jfrog.com/confluence/display/RTF/Artifactory+REST+API#ArtifactoryRESTAPI-GetUserDetails
[delegateAuthenticationRealm]: https://github.com/JFrogDev/artifactory-user-plugins/tree/master/security/delegateAuthenticationRealm
