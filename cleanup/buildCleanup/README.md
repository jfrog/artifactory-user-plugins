Artifactory Build Cleanup User Plugin
=====================================

This plugin deletes all builds that are older than n days. It can be run
manually from the REST API, or automatically as a scheduled job.

Parameters
----------

- `days`: The number of days back to look before deleting a build. Default 2.
- `dryRun`: If this parameter is passed, builds will not actually be deleted.

Properties
----------

- `days`: The number of days back to look before deleting a build.
- `dryRun`: If this property is true, builds will not actually be deleted.

Executing
---------

To execute the plugin:

`curl -X POST -v -u admin:password "http://localhost:8080/artifactory/api/plugins/execute/clean-builds?params=days=50|dryRun"`
