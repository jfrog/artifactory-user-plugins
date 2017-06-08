Artifactory Artifact Cleanup User Plugin
========================================

This plugin deletes all artifacts that have not been downloaded for the past n
months. It can be ran manually from the REST API, or automatically as a
scheduled job. Job schedule is read from a similarly named property file.

Many delete operations can affect performance due to disk I/O occurring. A new parameter now allows a delay per delete operation. See below.

To ensure logging for this plugin, edit `${ARTIFACTORY_HOME}/etc/logback.xml` to add:
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

Execution
---------

To execute the plugin manually:

```
curl -v -G -u admin:password -X POST \
--data-urlencode "params=months=1|repos=libs-release,libs-snapshot" \
"http://localhost:8081/artifactory/api/plugins/execute/cleanup"
```

There is also ability to control the running script.

```
curl -v -G -u admin:password -X POST \
--data-urlencode "params=command=<command>" \
"http://localhost:8081/artifactory/api/plugins/execute/cleanupCtl"
```

where `command` is one of the following:

Operations
---------

The plugin has 4 control options:

- `stop`: When detected, the loop deleting artifacts is exited and the script ends.

- `pause`: Suspend operation. The thread continues to run with a 1 minute sleep for retest.

- `resume`: Resume normal execution.

- `adjustPaceTimeMS`: Modify the running delay factor by increasing/decreasing the delay value. Example:

```
curl -v -G -u admin:password -X POST \
--data-urlencode "params=command=adjustPaceTimeMS|value=-1000" \
"http://localhost:8081/artifactory/api/plugins/execute/cleanupCtl"
```
