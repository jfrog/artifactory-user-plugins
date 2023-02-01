Artifactory Dummy User Plugin
=============================

A simple dummy plugin. Responds to calls via the REST API with the JSON message
`{"status":"okay"}`.

Executing
---------

To execute the plugin: 
'curl -X POST http://localhost:8081/artifactory/api/plugins/execute/dummyPlugin'


Refer to https://www.jfrog.com/confluence/display/RTF/User+Plugins for additional information on developing your own Artifactory User Plugin. 
