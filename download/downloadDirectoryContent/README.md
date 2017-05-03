Artifactory Download Directory Content User Plugin
==================================================

This plugin allows you to download a directory tree from Artifactory, in the
form of a zip file. To do this, you must include the matrix parameter
`downloadDirectory+=true` in your request, like so:

`curl -X GET -uadmin:password "http://localhost:8081/artifactory/libs-release-local/myDirectory;downloadDirectory+=true" > result.zip`

Installation
---------------------

This plugin needs to be added to the `$ARTIFACTORY_HOME/etc/plugins` directory.