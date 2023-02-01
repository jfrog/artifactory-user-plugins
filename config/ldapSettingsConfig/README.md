Artifactory LDAP Settings Config User Plugin
============================================

Allows REST access to the LDAP settings configuration. This plugin exposes five
executions:

getLdapSettingsList
-------------------

`getLdapSettingsList` returns a JSON list containing the keys of all the
currently configured LDAP settings.

For example:

```
$ curl -u admin:password 'http://localhost:8081/artifactory/api/plugins/execute/getLdapSettingsList'
[
    "newsetting"
]
```

getLdapSetting
--------------

`getLdapSetting` returns a JSON representation of the current configuration of a
given setting. The key of the requested setting is passed as the parameter
`key`. The returned JSON string has the following fields:

- `key`: The setting's unique ID key.
- `enabled`: Whether this setting is enabled.
- `ldapUrl`: Location of the LDAP server.
- `userDnPattern`: A DN pattern which can be used to log into LDAP.
- `searchFilter`: A filter used to search for the user DN.
- `searchBase`: Context name to search in.
- `searchSubTree`: Whether to perform deep searching.
- `managerDn`: The full DN of the user performing searches.
- `managerPassword`: The password for the above user.
- `autoCreateUser`: Whether users logged in with LDAP should be created
  automatically.
- `emailAttribute`: An attribute to map a user's email to an automatically
  created Artifactory user.

For example:

```
$ curl -u admin:password 'http://localhost:8081/artifactory/api/plugins/execute/getLdapSetting?params=key=newsetting'
{
    "key": "newsetting",
    "enabled": true,
    "ldapUrl": "ldap://somehost",
    "userDnPattern": "uid={0}",
    "searchFilter": null,
    "searchBase": null,
    "searchSubTree": false,
    "managerDn": null,
    "managerPassword": null,
    "autoCreateUser": false,
    "emailAttribute": "email"
}
```

deleteLdapSetting
-----------------

`deleteLdapSetting` deletes a setting from the Artifactory instance. The key of
the setting to delete is passed as the parameter `key`.

For example:

```
$ curl -u admin:password -X DELETE 'http://localhost:8081/artifactory/api/plugins/execute/deleteLdapSetting?params=key=newsetting'
```

addLdapSetting
--------------

`addLdapSetting` adds a new setting to the Artifactory instance. The setting to
add is defined by a JSON object sent in the request body, with the same schema
used by `getLdapSetting`.

For example:

```
$ curl -u admin:password -X POST -H 'Content-Type: application/json' -d '{
> "key": "newsetting",
> "enabled": true,
> "ldapUrl": "ldap://somehost",
> "userDnPattern": "uid={0}",
> "searchFilter": null,
> "searchBase": null,
> "searchSubTree": false,
> "managerDn": null,
> "managerPassword": null,
> "autoCreateUser": false,
> "emailAttribute": "email"
> }' 'http://localhost:8081/artifactory/api/plugins/execute/addLdapSetting'
```

updateLdapSetting
-----------------

`updateLdapSetting` updates an existing setting. The key of the setting to
modify is passed as the parameter `key`. The modifications are defined by a JSON
object sent in the request body, with the same schema used by `getLdapSetting`
and `addLdapSetting`. Only the fields that should be modified need to be
included in the JSON representation, and all other fields will be preserved.

For example:

```
$ curl -u admin:password -X POST -H 'Content-Type: application/json' -d '{
> "ldapUrl": "ldap://someotherhost",
> "autoCreateUser": true
> }' 'http://localhost:8081/artifactory/api/plugins/execute/updateLdapSetting?params=key=newsetting'
```
