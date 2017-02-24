Artifactory Semantic Versioning Check User Plugin
=======================================

*This plugin is currently only working in Artifactory 4.x. We are working on updating it to work in Artifactory 5.x.*

This plugin performs binary compatibility checks when artifact is created in Artifactory. Property binaryCompatibleWith or binaryIncompatibleWith will be added with the version of artifact that the check was performed against as a value.

Installation
---------------------------------------
This plugin needs to be added to the `$ARTIFACTORY_HOME/etc/plugins directory`.