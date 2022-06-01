Artifactory SAML Config User Plugin
===================================

Allows REST access to the SAML configuration. This plugin exposes two
executions:

getSaml
-------

`getSaml` returns a JSON representation of the current SAML configuration. The
returned JSON string has the following fields:

- `enableIntegration`: Whether SAML is enabled
- `loginUrl`: The SAML login URL
- `logoutUrl`: The SAML logout URL
- `serviceProviderName`: The SAML service provider name
- `noAutoUserCreation`: Whether to automatically create users on SAML login
- `certificate`: The SAML certificate as a base64 string
- `groupAttribute`: The group attribute in the SAML login XML response
- `emailAttribute`: If Auto Create Artifactory Users is enabled or an internal user exists, the system will set the user’s email to the value in this attribute that is returned by the SAML login XML response.

For example:

```
$ curl -u admin:password 'http://localhost:8081/artifactory/api/plugins/execute/getSaml'
{
    "enableIntegration": true,
    "loginUrl": "http://mylogin",
    "logoutUrl": "http://mylogout",
    "serviceProviderName": "my-service-provider",
    "noAutoUserCreation": true,
    "certificate": "my-certificate",
    "groupAttribute": "groups",
    "emailAttribute": "email"
}
```

setSaml
-------

`setSaml` updates the current SAML configuration using the provided JSON object.
This object may have any combination of the fields listed above, and only the
provided fields will be updated in the configuration.

For example:

```
$ curl -u admin:password -X POST -H 'Content-Type: application/json' -d '{
> "enableIntegration": true,
> "loginUrl": "http://mylogin",
> "logoutUrl": "http://mylogout",
> "serviceProviderName": "my-service-provider",
> "noAutoUserCreation": true,
> "certificate": "my-certificate"
> }' 'http://localhost:8081/artifactory/api/plugins/execute/setSaml'
```
