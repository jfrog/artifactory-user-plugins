Artifactory Artifact Cleanup User Plugin
========================================

This plugin deletes all artifacts that have not been downloaded for the past n
months. It can be run manually from the REST API, or automatically as a
scheduled job.

Many delete operations can affect performance due to disk I/O occurring. A new parameter now allows a delay per delete operation. See below.

To ensure logging for this plugin, edit ${ARTIFACTORY_HOME}/etc/logback.xml to add:
```xml
    <logger name="artifactCleanup">
        <level value="info"/>
    </logger>
```

Parameters
----------

- `months`: The number of months back to look before deleting an artifact. Default 6.
- `repos`: A list of repositories to clean. This parameter is required.
- `dryRun`: If this parameter is passed, artifacts will not actually be deleted.
- `paceTimeMS`: The number of milliseconds to delay between delete operations. Default 0.
- `disablePropertiesSupport`: Disable the support of Artifactory Properties (see below *Artifactory Properties support* section). Default *false*.

Properties
----------

- `monthsUntil`: The number of months back to look before deleting an artifact.
- `repos`: A list of repositories to clean.
- `paceTimeMS`: The number of milliseconds to delay between delete operations.

Artifactory Properties support
----------

Some Artifactory [Properties](https://www.jfrog.com/confluence/display/RTF/Properties) are supported if defined on *artifacts* or *folders*:

- `cleanup.skip`: Skip the artifact deletion if property defined on artifact's path ; artifact itself or in a parent folder(s).


Executing
---------

To execute the plugin:

`curl -X POST -v -u admin:password "http://localhost:8080/artifactory/api/plugins/execute/cleanup?params=months=1;repos=libs-release-local;dryRun=true;paceTimeMS=2000;disablePropertiesSupport=true"`



There is also ability to control the running script. The following operations can occur

Operation
---------

The plugin have 4 control options:

- `stop`: When detected, the loop deleting artifacts is exited and the script ends. Example:

`curl -X POST -v -u admin:password "http://localhost:8080/artifactory/api/plugins/execute/cleanupCtl?params=command=stop"`
- `pause`: Suspend operation. The thread continues to run with a 1 minute sleep for retest. Example:

`curl -X POST -v -u admin:password "http://localhost:8080/artifactory/api/plugins/execute/cleanupCtl?params=command=pause"`
- `resume`: Resume normal execution. Example:

`curl -X POST -v -u admin:password "http://localhost:8080/artifactory/api/plugins/execute/cleanupCtl?params=command=resume"`
- `adjustPaceTimeMS`: Modify the running delay factor by increasing/decreasing the delay value. Example:


For Artifactory 4.x
`curl -X POST -v -u admin:password "http://localhost:8080/artifactory/api/plugins/execute/cleanupCtl?params=command=adjustPaceTimeMS|value=-1000"` 

For Artifactory 5.x
`curl -X POST -v -u admin:password "http://localhost:8080/artifactory/api/plugins/execute/cleanupCtl?params=command=adjustPaceTimeMS;value=-1000"` 
