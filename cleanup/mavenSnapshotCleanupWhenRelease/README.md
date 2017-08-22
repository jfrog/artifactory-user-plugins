Artifactory Maven Snapshot Cleanup When Release
=====================================

This plugin deletes Maven SNAPSHOT artifacts when the release version is published.


Features
--------

Consider the Maven snapshots repository `maven-local-lib-snapshots` populated like that:

    org/jfrog/test/my-artifact/1.0.0-SNAPSHOT/my-artifact-1.0.0-20170101.080829-1.pom
    org/jfrog/test/my-artifact/1.0.0-SNAPSHOT/my-artifact-1.0.0-20170101.080829-1.jar
    org/jfrog/test/my-artifact/1.0.0-SNAPSHOT/my-artifact-1.0.0-20170101.080829-2.pom
    org/jfrog/test/my-artifact/1.0.0-SNAPSHOT/my-artifact-1.0.0-20170101.080829-2.jar

    org/jfrog/test/my-artifact/1.1.0-SNAPSHOT/my-artifact-1.1.0-20170202.080829-1.pom
    org/jfrog/test/my-artifact/1.1.0-SNAPSHOT/my-artifact-1.1.0-20170202.080829-1.jar


When the **pom file** of `my-artifact` version `1.0.0` is deployed into `maven-local-lib-releases` (Maven releases repository), the associated snapshot is deleted.

e.g: folder `org/jfrog/test/my-artifact/1.0.0-SNAPSHOT/` of `maven-local-lib-snapshots` repository.

Installation
------------

To install this plugin:

* Place files `mavenSnapshotCleanupWhenRelease.groovy` and `mavenSnapshotCleanupWhenRelease.properties` under the master Artifactory server `${ARTIFACTORY_HOME}/etc/plugins`.

* Configure the file `mavenSnapshotCleanupWhenRelease.properties` with the repositories couples *releases <-> snapshots*. In a couple, the first should be the *release* repository and the second the *snapshot* repository:
```
    repositories =  [ ["maven-local-lib-releases","maven-local-lib-snapshots"], ["maven-local-plugin-releases","maven-local-plugin-snapshots"] ]
```
 
* Configure the logger in `${ARTIFACTORY_HOME}/etc/logback.xml` with level wanted:

```
    <configuration ...>
        ...
        <logger name="mavenSnapshotCleanupWhenRelease">
            <level value="info"/>
        </logger>   
    </configuration>
```

* Verify in the `${ARTIFACTORY_HOME}/logs/artifactory.log` that the plugin and configuration loaded correctly.

Execution
---------

The plugin deletion process is called when an artifact (pom and associated files like a jar, ...) is deployed in a configured Maven release repository, precisely when POM file is deployed. 

The plugin configuration could be updated dynamically, using `api/plugins/execute/mavenSnapshotCleanupWhenReleaseConfig` URL with `params`:

- `repositories`: The list of couple like in `mavenSnapshotCleanupWhenRelease.properties`, but with braquet/quote/comma URL encoded
- `action`: (Optional) `reset`current configuration before do the `add` (default action) 

Example:

    curl -i -uadmin:password -X POST "http://localhost:8081/artifactory/api/plugins/execute/mavenSnapshotCleanupWhenReleaseConfig?params=action=reset;repositories=%5B%5B%22maven-local-lib-releases%22%2C%22maven-local-lib-snapshots%22%5D%5D"
