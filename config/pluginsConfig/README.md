Artifactory Config Plugins User Plugin
=====================================

This plugin contains two execution commands that can be run manually from the REST API.

1. Command listPlugins returns to the user a list of all installed plugins on
   a given artifactory instance
2. Command downloadPlugin returns the contants of the plugin script to the user
   on a given artifactory instance

Parameters
----------

- `name`: This is the plugin name that the user provides to return the contents
  of the plugin

Executing
---------

To execute the list plugins command:

`curl -X POST -v -u admin:password "http://localhost:8080/artifactory/api/plugins/execute/listPlugins"`

To execute the download plugin's command:

`curl -X GET -v -u admin:password "http://localhost:8080/artifactory/api/plugins/execute/downloadPlugin?params=name=<pluginName>"`
