Artifactory User Plugin to search artifacts by sha1 and return GAVC
====================================================================

This plugin search artifacts by sha1 and return GAVC of artifact.

Command to use plugin:

```
curl -u admin:password http://localhost:8081/artifactory/api/plugins/execute/getGavcBySha1?params=sha1=cf2171cba8bbbdf7f423f9ef54d8626e4011fd96
```