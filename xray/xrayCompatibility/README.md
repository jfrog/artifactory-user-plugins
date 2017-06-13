Artifactory Xray Compatibility User Plugin
==============================

This plugin tests for an Artifactory version to see if all the Xray features are supported by this particular Artifactiory version. It checks for all the rest points which are accessible to Xray from Artifactory.

1. Print plugin version

```curl -v -XGET -uadmin:password "http://localhost:8081/artifactory/api/plugins/execute/xrayCompatibilityVersion"```

2. Index all the artifacts in the given repos

```curl -v -XGET -uadmin:password "http://127.0.0.1:8081/artifactory/api/plugins/execute/xrayIndex?params=repos=libs-release-local,libs-snapshot-local"```
