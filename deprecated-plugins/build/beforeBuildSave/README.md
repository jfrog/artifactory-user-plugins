Artifactory Before Build Save User Plugin
=========================================

Whenever a build is submitted, this plugin removes any parameters from the MIME
types of any of the artifacts in the build.

e.g The build.json file contains the information type "war;x=10".
 
When uploaded the type will be "war"

Installation
------------

Place plugin under ${ARTIFACTORY_HOME}/etc/plugins/