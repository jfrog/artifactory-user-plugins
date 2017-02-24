Artifactory Repo Stats User Plugin
==================================

*This plugin is currently only working in Artifactory 4.x. We are working on updating it to work in Artifactory 5.x.*

This plugin displays certain statistics about requested files via the REST API.
Given one or more file paths, this plugin will show the number of artifacts and
the combined filesize of those artifacts at each of those locations.

Parameters
----------

This plugin takes one parameter, called `paths`, which consists of a
comma-separated list of paths to show stats for. Each path is in the form
`repository-name/path/to/file`.

Executing
---------

To execute the plugin (as an example):

`curl -X POST -uadmin:password http://localhost:8088/artifactory/api/plugins/execute/repoStats?params=paths=libs-release-local/dir/path1,libs-release-local/dir/path2`

The response is a JSON object in the following form:
```JSON
{
    "stats": [
        {
            "repoPath": "libs-release-local/dir/path1",
            "count": 2,
            "size": "21 bytes"
        },
        {
            "repoPath": "libs-release-local/dir/path2",
            "count": 1,
            "size": "10 bytes"
        }
    ]
}
```
