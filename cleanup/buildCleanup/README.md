Artifactory Build Cleanup User Plugin
=====================================

This plugin deletes all builds that are older than n days. It can be run
manually from the REST API, or automatically as a scheduled job.

**&ast;&ast;&ast; DEPRECATION NOTICE &ast;&ast;&ast;**

*Artifactory 6.6 introduced [Build Info Repositories](https://www.jfrog.com/confluence/display/RTF/Release+Notes#ReleaseNotes-Artifactory6.6).  With this introduction, standard artifact cleanup techniques such as via the [CLI](https://jfrog.com/blog/aql-cli-a-match-made-in-heaven/) or via the [artifactCleanup plugin](https://github.com/markgalpin/artifactory-user-plugins/tree/master/cleanup/artifactCleanup) should work equivalently to this plugin, so we are deprecating this plugin.*

*Documentation and plugin is preserved here for users of older versions.*

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


For Artifactory 4.x:


`curl -X POST -v -u admin:password "http://localhost:8080/artifactory/api/plugins/execute/cleanBuilds?params=days=50|dryRun"` 


For Artifactory 5.x:
`curl -X POST -v -u admin:password "http://localhost:8080/artifactory/api/plugins/execute/cleanBuilds?params=days=50;dryRun"`  
