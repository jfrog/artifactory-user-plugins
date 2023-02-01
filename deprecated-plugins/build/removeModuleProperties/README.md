Artifactory Remove Module Properties User Plugin
================================================

This plugin will remove all the properties inside the `buildInfo.json` of all
the build's particular modules. This will not remove the properties of the
build's modules that Artifactory assigns.

#### Installation
To install this plugin:
  - Place the script under the master Artifactory instance in the
  `${ARTIFACTORY_HOME}/etc/plugins`
  - Verify in the `${ARTIFACTORY_HOME}/logs/artifactory.log`, after starting up
  the instance, that the plugin was loaded correctly
  
