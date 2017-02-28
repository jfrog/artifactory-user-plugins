Artifactory Delete Deprecated User Plugin
=========================================

This plugin deletes all artifacts marked with the property
`analysis.deprecated=true`.

Executing
---------

To execute the plugin:

`curl -X POST -v -u admin:password "http://localhost:8080/artifactory/api/plugins/execute/deleteDeprecatedPlugin"`
