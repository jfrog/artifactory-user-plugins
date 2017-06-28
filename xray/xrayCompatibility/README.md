Artifactory Xray Compatibility User Plugin
==============================

**This plugin is no longer supported. If you would like to use Artifactory with Xray, please upgrade to version 4.12 or higher.**

This plugin tests for an Artifactory version to see if all the Xray features are supported by this particular Artifactiory version. It checks for all the rest points which are accessible to Xray from Artifactory.

1. Print plugin version

```curl -v -XGET -uadmin:password "http://localhost:8081/artifactory/api/plugins/execute/xrayCompatibilityVersion"```

2. Index all the artifacts in the given repos

```curl -v -X POST -uadmin:password "http://localhost:8081/artifactory/api/plugins/execute/xrayIndex?params=repos=libs-release-local,libs-snapshot-local"```

3. Trigger removal of all current Xray index tasks

```curl -v -X DELETE -uadmin:password "http://localhost:8081/artifactory/api/plugins/execute/xrayClearAllIndexTasks"```
