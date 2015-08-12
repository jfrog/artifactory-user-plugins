Artifactory Delete Empty Dirs User Plugin
=========================================

This plugin deletes all empty directories found inside any of a given set of
paths.

Parameters
----------

This plugin takes one parameter, called `paths`, which consists of a
comma-separated list of paths to search for empty directories in. Each path is
in the form `repository-name/path/to/dir`.

Executing
---------

To execute the plugin:

`curl -X POST -v -u admin:password "http://localhost:8080/artifactory/api/plugins/execute/deleteEmptyDirsPlugin?params=paths=repo,otherRepo/some/path"`
