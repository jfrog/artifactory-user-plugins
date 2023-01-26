Artifactory SAML Config User Plugin
===================================

Allows REST access to the SAML configuration. This plugin exposes two
executions:

getSaml
-------

`getSaml` returns a JSON representation of the current SAML configuration. The
returned JSON string has the following fields:

- `enableIntegration`: Whether SAML is enabled
- `verifyAudienceRestriction`: A verification step has been set up opposite the SAML server to validate SAML SSO authentication requests
- `loginUrl`: The SAML login URL
- `logoutUrl`: The SAML logout URL
- `certificate`: The SAML certificate as a base64 string
- `serviceProviderName`: The SAML service provider name
- `noAutoUserCreation`: Whether to automatically create users on SAML login
- `allowUserToAccessProfile`: When selected, users created after authenticating using SAML, will be able to access their profile
- `useEncryptedAssertion`: When set, an X.509 public certificate will be created by Artifactory. Download this certificate and upload it to your IDP and choose your own encryption algorithm. This process will let you encrypt the assertion section in your SAML response
- `autoRedirect`: When set, clicking on the login link will direct the users to the configured SAML login URL
- `syncGroups`: When set, in addition to the groups the user is already associated with, they will also be associated with the groups returned in the SAML login response
- `groupAttribute`: The group attribute in the SAML login XML response
- `emailAttribute`: If Auto Create Artifactory Users is enabled or an internal user exists, the system will set the user’s email to the value in this attribute that is returned by the SAML login XML response.

For example:

```
$ curl -u admin:password 'http://localhost:8081/artifactory/api/plugins/execute/getSaml'
{
    "enableIntegration": true,
    "verifyAudienceRestriction": true,
    "loginUrl": "http://mylogin",
    "logoutUrl": "http://mylogout",
    "certificate": "my-certificate",    
    "serviceProviderName": "my-service-provider",
    "noAutoUserCreation": true,
    "allowUserToAccessProfile": false,
    "useEncryptedAssertion": false,
    "autoRedirect": false,
    "syncGroups": true,
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
