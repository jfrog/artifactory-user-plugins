Artifactory Build Property Setter User Plugin
=============================================

Whenever a build is published, the artifacts from the most recent run of the
build are all marked with the property `latest:true`.

#### Installation
To install this plugin:
  - Place the script under the master Artifactory instance in the
  `${ARTIFACTORY_HOME}/etc/plugins`
  - Verify in the `${ARTIFACTORY_HOME}/logs/artifactory.log`, after starting up
  the instance, that the plugin was loaded correctly
