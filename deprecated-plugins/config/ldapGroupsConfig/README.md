Artifactory LDAP Groups Config User Plugin
==========================================

Allows REST access to the LDAP groups configuration. This plugin exposes five
executions:

getLdapGroupsList
-----------------

`getLdapGroupsList` returns a JSON list containing the names of all the
currently configured LDAP groups.

For example:

```
$ curl -u admin:password 'http://localhost:8081/artifactory/api/plugins/execute/getLdapGroupsList'
[
    "newgroup"
]
```

getLdapGroup
------------

`getLdapGroup` returns a JSON representation of the current configuration of a
given group. The name of the requested group is passed as the parameter `name`.
The returned JSON string has the following fields:

- `name`: The group's unique ID name.
- `groupBaseDn`: A search base for group entry DNs.
- `groupNameAttribute`: An attribute containing the group name.
- `filter`: The LDAP filter used to search for group entries.
- `groupMemberAttribute`: An attribute containing group member DNs or IDs.
- `subTree`: Whether to perform deep searching.
- `descriptionAttribute`: An attribute containing the group description.
- `strategy`: The mapping strategy. Value can be `"STATIC"`, `"DYNAMIC"`, or
  `"HIERARCHY"`.
- `enabledLdap`: The LDAP setting to use for group retrieval.

For example:

```
$ curl -u admin:password 'http://localhost:8081/artifactory/api/plugins/execute/getLdapGroup?params=name=newgroup'
{
    "name": "newgroup",
    "groupBaseDn": null,
    "groupNameAttribute": "cn",
    "filter": "(objectClass=groupOfNames)",
    "groupMemberAttribute": "uniqueMember",
    "subTree": true,
    "descriptionAttribute": "description",
    "strategy": "STATIC",
    "enabledLdap": null
}
```

deleteLdapGroup
---------------

`deleteLdapGroup` deletes a group from the Artifactory instance. The name of the
group to delete is passed as the parameter `name`.

For example:

```
$ curl -u admin:password -X DELETE 'http://localhost:8081/artifactory/api/plugins/execute/deleteLdapGroup?params=name=newgroup'
```

addLdapGroup
------------

`addLdapGroup` adds a new group to the Artifactory instance. The group to add is
defined by a JSON object sent in the request body, with the same schema used by
`getLdapGroup`.

For example:

```
$ curl -u admin:password -X POST -H 'Content-Type: application/json' -d '{
> "name": "newgroup",
> "groupBaseDn": null,
> "groupNameAttribute": "cn",
> "filter": "(objectClass=groupOfNames)",
> "groupMemberAttribute": "uniqueMember",
> "subTree": true,
> "descriptionAttribute": "description",
> "strategy": "STATIC",
> "enabledLdap": null
> }' 'http://localhost:8081/artifactory/api/plugins/execute/addLdapGroup'
```

updateLdapGroup
---------------

`updateLdapGroup` updates an existing group. The name of the group to modify is
passed as the parameter `name`. The modifications are defined by a JSON object
sent in the request body, with the same schema used by `getLdapGroup` and
`addLdapGroup`. Only the fields that should be modified need to be included in
the JSON representation, and all other fields will be preserved.

For example:

```
$ curl -u admin:password -X POST -H 'Content-Type: application/json' -d '{
> "subTree": false,
> "strategy": "DYNAMIC"
> }' 'http://localhost:8081/artifactory/api/plugins/execute/updateLdapGroup?params=name=newgroup'
```
