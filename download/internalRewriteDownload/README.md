Artifactory Internal Rewrite Download User Plugin
=================================================

This plugin creates a virtual symlink called "latest", which redirects to the
directory provided from the value of the `latest.folderName` property set on the
root folder of the repository. 

## Features
In this example, this only works in the `dist-local` repository. However, this script will work for any local repo by:
- Changing `.repositorySettings(new GenericRepositorySettingsImpl()).build()` to the package type you're using (e.g. if you are creating a Maven repository -> `.repositorySettings(new MavenRepositorySettingsImpl()).build()`
- Changing the `dist-local` repo name 

#### Installation
To install this plugin:
  - Place the script under the master Artifactory instance in the
  `${ARTIFACTORY_HOME}/etc/plugins`
  - Verify in the `${ARTIFACTORY_HOME}/logs/artifactory.log` that the
  plugin loaded correctly

