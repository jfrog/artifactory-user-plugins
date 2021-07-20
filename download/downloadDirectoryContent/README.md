DEPRECATED - Artifactory Download Directory Content User Plugin
==================================================

This plugin allows you to download a directory tree from Artifactory, in the
form of a zip file. To do this, you must include the matrix parameter
`downloadDirectory+=true` in your request, like so:

`curl -X GET -uadmin:password "http://localhost:8081/artifactory/libs-release-local/myDirectory;downloadDirectory+=true" > result.zip`

Installation
---------------------

This plugin needs to be added to the `$ARTIFACTORY_HOME/etc/plugins` directory.

==================================================

This plugin is now deprecated and will not be maintained as there is the ability to download a complete folder via UI and REST API (since Artifactory version 4.1.0): 
https://www.jfrog.com/confluence/display/RTF/Artifactory+REST+API#ArtifactoryRESTAPI-RetrieveFolderorRepositoryArchive
