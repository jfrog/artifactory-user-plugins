# Artifactory Migration Helper User Plugin

This plugin automatically sets replication from one Artifactory to another. It was created to help users to migrate to a new Artifactory installation.

## Features

This plugin provides the following features:

- Copy all repositories (local, remote and virtual) from the source Artifactory to the target
- Set event based push replication for every local repository from the source Artifactory to the target

During the copy repository step, the plugin will ignore everything that has already been added to the target Artifactory. So changes to repositories that have already been handled by this plugin must be done manually at the target Artifactory environment.

This plugin copies the virtual repositories in an arbitrary manner, so more than one execution may be necessary in order to successfully copy virtual repositories that have other virtual repositories in their compositions.

To minimize concurrent execution of replications, this plugin distribute the replications over time, according to parameters specified by the user in the `artifactoryMigrationHelper.json` configuration file. These parameters are:

- **replicationInitialHour**: left endpoint (inclusive) of the interval in hours where the user wants replications to start.
    - Possible values: Any integer between [0-23]
    - Default value: 0
- **replicationFinalHour**: right endpoint (exclusive) of the interval in hours where the user wants replications to start.
    - Possible values: Any integer between [1-24]
    - Default value: 24
- **replicationTimes**: Number of times the user wants replication of every repository to start into the given interval.
    - Possible values: Any integer bigger or equal to 1
    - Default value: 1
- **replicationStep**: Number of minutes between one repository replication start time to another.
    - Possible values: Any integer bigger or equal to 1
    - Default value: 30

The parameters **replicationInitialHour** and **replicationFinalHour** can be used to limit the period of the day where the user wants replications to start. The default configuration file provided will set repository replications to start once a day with a 30 minutes gap between starts for different repositories.

The others parameters present in the configuration file are:

- **cron**: Cron expression to schedule the execution of this plugin.
    - Possible values: Any valid [cron expression](http://www.quartz-scheduler.org/documentation/quartz-1.x/tutorials/crontrigger)
    - Default value: Every hour: `0 0 0/1 * * ?`
- **target**: Target Artifactory url.
    - Possible values: Url to valid Artifactory installation, including context path
    - Default value: http://localhost:8081/artifactory
- **username**: Username to be used to perform REST API calls and setup replication to the target Artifactory. Admin privileges are required.
    - Possible values: Any valid username with admin privileges
    - Default value: admin
- **password**: Password to be used to perform REST API calls and setup replication to the target Artifactory.
    - Possible values: Any string
    - Default value: password

## Limitations

This user plugin does not handle repository layouts. In order for this plugin to work properly, the user must manually sync the repository layouts between the source and the target Artifactory installations.

## Installation

To install this plugin:

1. Place file `artifactoryMigrationHelper.json` under the master Artifactory server `${ARTIFACTORY_HOME}/etc/plugins`
2. Edit `artifactoryMigrationHelper.json` file content according to your preferences/environment
3. Place file `artifactoryMigrationHelper.groovy` under the master Artifactory server `${ARTIFACTORY_HOME}/etc/plugins`
4. Request [user plugins reload](https://www.jfrog.com/confluence/display/RTF/Artifactory+REST+API#ArtifactoryRESTAPI-ReloadPlugins)
2. Verify in the system logs that the plugin loaded correctly.

### Logging

To enable logging, add the following lines to `$ARTIFACTORY_HOME/etc/logback.xml` file. There is no need to restart Artifactory for this change to take effect:

```xml
<logger name="artifactoryMigrationHelper">
    <level value="info"/>
</logger>
```

## Usage

This plugin will run automatically according to the cron expression configured in the configuration file.

To manually request its execution, the following API can be used:

```
curl -X POST -v -u user:password "http://localhost:8080/artifactory/api/plugins/execute/artifactoryMigrationSetup"`
```
