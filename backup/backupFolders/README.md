Artifactory Backup Folders User Plugin
======================================

Provides you the ability to backup specific path within the repository.

Adding to Artifactory
---------------------

This plugin need to be added to the `$ARTIFACTORY_HOME/etc/plugins` directory.

Log information
---------------

Please add the following to the `$ARTIFACTORY_HOME/etc/logback.xml` file:

For more verbosity logs:

```XML
<logger name="backUpFolder">
    <level value="debug"/>
</logger>
```

For log level info:

```XML
<logger name="backUpFolder">
    <level value="info"/>
</logger>
```

This would not require restart of the Artifactory Server.

Firing up the plugin
--------------------

This plugin has the ability to be executed in two ways:

1. At a specific time with the job section (uses cron expression).
2. By REST command.

Executing
---------

To execute this plugin:

```
curl -X GET -u{admin_user}:{password} "http://{ARTIFACTORY_URL}:{PORT}/artifactory/api/plugins/execute/backup" -T properties.json
```

When the `properties.json` file need to include two parameters:

1. Destination folder. For example: For windows use two backslashes: `"destinationFolder":"c:\\Work\\Test"`.
2. Path to back up. For example: `"pathToFolder":"{repository_name}/{path}/{to}/{backup}"`.

Jobs
----

This plugin by default is set to be fired up each day at 1:00 AM
(`0 0 1 1/1 * ? *`). You can modify this within the plugin. The job execution
point uses the properties file to retrieve the destination folder and the path:

1. Destination folder. For example: For windows use two back slashes: `"destinationFolder":"c:\\Work\\Test"`
2. Path to back up. For example: `"pathToFolder":"{repository_name}/{path}/{to}/{backup}"`
