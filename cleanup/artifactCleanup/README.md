Artifactory Artifact Cleanup User Plugin
========================================

This plugin deletes all artifacts that have not been downloaded for the past n
months. It can be run manually from the REST API, or automatically as a
scheduled job.

Parameters
----------

- `months`: The number of months back to look before deleting an artifact. Default 6.
- `repos`: A list of repositories to clean. This parameter is required.
- `dryRun`: If this parameter is passed, artifacts will not actually be deleted.

Properties
----------

- `monthsUntil`: The number of months back to look before deleting an artifact.
- `repos`: A list of repositories to clean.

Executing
---------

To execute the plugin:

`curl -X POST -v -u admin:password "http://localhost:8080/artifactory/api/plugins/execute/cleanup?params=months=1|repos=libs-release-local|dryRun"`
