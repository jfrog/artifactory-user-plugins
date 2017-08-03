Artifactory Build Tagging Promotion User Plugin
==============================================

This plugin performs tagging and promotion of war files which can later be resolved by one single URL using [property-based resolution](https://www.jfrog.com/confluence/display/RTF/Using+Properties+in+Deployment+and+Resolution).

Installation
------------

To install this plugin:

1. Place this script under the master Artifactory server `${ARTIFACTORY_HOME}/etc/plugins`.
2. Verify in the `${ARTIFACTORY_HOME}/logs/artifactory.log` that the plugin loaded correctly.

Features
--------

This plugin defines a promotion named `cloudPromote`. This promotion expects the following parameters:

- targetRepository: Repository to copy the build's artifacts
- staging: Value for tag `staging`
- oss: Value for tag `oss`
- prod: Value for tag `prod`

After receiving a promotion request, the plugin will copy the build's war file artifacts to the designated target repository. For each value of tag parameters, a new property called `aol.<flag>` will be added to the promoted artifacts.

Execution
---------

To execute this plugin use the [Execute Build Promotion](https://www.jfrog.com/confluence/display/RTF/Artifactory+REST+API#ArtifactoryRESTAPI-ExecuteBuildPromotion) method from Artifactory Rest API.

Example:

`curl -X POST -v -u user:password "http://localhost:8080/artifactory/api/plugins/build/promote/cloudPromote/<BUILD_NAME>/<BUILD_NUMBER>?params=targetRepository=<TARGET_REPO>|prod=true"` for Artifactory 4.x, and `curl -X POST -v -u user:password "http://localhost:8080/artifactory/api/plugins/build/promote/cloudPromote/<BUILD_NAME>/<BUILD_NUMBER>?params=targetRepository=<TARGET_REPO>;prod=true"` for Artifactory 5.x. 
