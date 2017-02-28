Artifactory Repo Layouts Config User Plugin
===========================================

Allows REST access to the layout configuration. This plugin exposes five
executions:

getLayoutsList
--------------

`getLayoutsList` returns a JSON list containing the names of all the currently
configured layouts.

For example:

```
$ curl -u admin:password 'http://localhost:8081/artifactory/api/plugins/execute/getLayoutsList'
[
    "maven-2-default",
    "ivy-default",
    "gradle-default",
    "maven-1-default",
    "newlayout"
]
```

getLayout
---------

`getLayout` returns a JSON representation of the current configuration of a
given layout. The name of the requested layout is passed as the parameter
`name`. The returned JSON string has the following fields:

- `name`: The layout's unique ID name.
- `artifactPathPattern`: The pattern that matches the storage path of artifacts.
- `distinctiveDescriptorPathPattern`: Whether descriptor files have distinctive
  path patterns.
- `descriptorPathPattern`: If `distinctiveDescriptorPathPattern` is `true`, the
  path pattern for descriptor files.
- `folderIntegrationRevisionRegExp`: A regular expression which matches an
  integration revision string appearing in a folder name.
- `fileIntegrationRevisionRegExp`: A regular expression which matches an
  integration revision string appearing in a file name.

For example:

```
$ curl -u admin:password 'http://localhost:8081/artifactory/api/plugins/execute/getLayout?params=name=newlayout'
{
    "name": "newlayout",
    "artifactPathPattern": "[org].[module].[baseRev].[ext]",
    "distinctiveDescriptorPathPattern": false,
    "descriptorPathPattern": null,
    "folderIntegrationRevisionRegExp": ".*",
    "fileIntegrationRevisionRegExp": ".*"
}
```

deleteLayout
------------

`deleteLayout` deletes a layout from the Artifactory instance. The name of the
layout to delete is passed as the parameter `name`.

For example:

```
$ curl -u admin:password -X DELETE 'http://localhost:8081/artifactory/api/plugins/execute/deleteLayout?params=name=newlayout'
```

addLayout
---------

`addLayout` adds a new layout to the Artifactory instance. The layout to add is
defined by a JSON object sent in the request body, with the same schema used by
`getLayout`.

For example:

```
$ curl -u admin:password -X POST -H 'Content-Type: application/json' -d '{
> "name": "newlayout",
> "artifactPathPattern": "[org].[module].[baseRev].[ext]",
> "distinctiveDescriptorPathPattern": false,
> "descriptorPathPattern": null,
> "folderIntegrationRevisionRegExp": ".*",
> "fileIntegrationRevisionRegExp": ".*"
> }' 'http://localhost:8081/artifactory/api/plugins/execute/addLayout'
```

updateLayout
------------

`updateLayout` updates an existing layout. The name of the layout to modify is
passed as the parameter `name`. The modifications are defined by a JSON object
sent in the request body, with the same schema used by `getLayout` and
`addLayout`. Only the fields that should be modified need to be included in the
JSON representation, and all other fields will be preserved.

For example:

```
$ curl -u admin:password -X POST -H 'Content-Type: application/json' -d '{
> "distinctiveDescriptorPathPattern": true,
> "descriptorPathPattern": "[org].[module].[baseRev].foo"
> }' 'http://localhost:8081/artifactory/api/plugins/execute/updateLayout?params=name=newlayout'
```
