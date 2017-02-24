Artifactory Backup Folders User Plugin
======================================

*This plugin is currently only working in Artifactory 4.x. We are working on updating it to work in Artifactory 5.x.*

Provides you the ability to prevent override of existing artifacts to non admin users.

Adding to Artifactory
---------------------

This plugin need to be added to the `$ARTIFACTORY_HOME/etc/plugins` directory.

Log information
---------------

Please add the following to the `$ARTIFACTORY_HOME/etc/logback.xml` file:

For log level info:

```XML
 <logger name="preventOverride">
    <level value="info"/>
</logger>
```

This would not require restart of the Artifactory Server.

The Properties File
-------------------

Inside the properties file you can configure the paths that you want to prevent from non admin users to override artifacts.
This can configured on a repository level or for a specific path:

Exmample:
-----------

repos = [ "libs-release-local", "ForTest/prevent/override" ]

This will prevent override in the entire "libs-release-local" repository and for the path "prevent/override" in the "ForTest" repository.

In the logs:
-------------

The following can be seen in the logs:

```XML
[INFO ] (o.a.e.UploadServiceImpl:457) Deploy to 'ForTest:prevent/override/test.txt' Content-Length: 54
[INFO ] (preventOverride     :61) The file exists already, only Administrator can override
[ERROR] (o.a.r.i.s.StorageInterceptorsImpl:56) Before create rejected: This item already exists in the following path: ForTest/prevent/override/: This item already exists in the following path: ForTest/prevent/override/
[WARN ] (o.a.r.ArtifactoryResponseBase:107) Sending HTTP error code 403: org.artifactory.exception.CancelException: This item already exists in the following path: ForTest/prevent/override/
```

If you deploy via REST:
--------------------------

```XML
{
  "errors" : [ {
    "status" : 403,
    "message" : "org.artifactory.exception.CancelException: This item already exists in the following path: ForTest/prevent/override/"
  } ]
}
``` 
