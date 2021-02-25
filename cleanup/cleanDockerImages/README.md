Artifactory Clean Docker Images User Plugin
===========================================

This plugin is used to clean Docker virtual repositories putting the number of images that we want to keep in the plugin itself, by default 3
policies.

Configuration
-------------
In the plugin itself we have to define the virtual repository in repoGlobal and the cron in quartz format.

In the plugin we need to change the return getMaxCountForDelete for put the max images that we need. 
Usage
-----

By cron and Cleanup can be triggered via a REST endpoint. For example:

``` shell
curl -XPOST -uadmin:password http://localhost:8081/artifactory/api/plugins/execute/cleanDockerImages
```

A dry run can also be triggered:

``` shell
curl -XPOST -uadmin:password "http://localhost:8081/artifactory/api/plugins/execute/cleanDockerImages?params=dryRun=true"
```
