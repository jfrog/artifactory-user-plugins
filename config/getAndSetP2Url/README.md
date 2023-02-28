Artifactory User Plugin to get and set Urls for P2 repositories 
===============================================================

User plugin to get urls included in P2 repositories and add/set urls to P2 repositories.

This version of the plugin works with Artifactory 7.48  and above.

Command to get Urls of P2 repository:
```
curl -uadmin:password -X POST "http://localhost:8081/artifactory/api/plugins/execute/getP2Urls?params=repo=repoKey"
```

Command to set Urls of P2 repository:
```
curl -uadmin:password -X POST --data-binary "{\"repo\": \"repoKey\",
 \"urls\": [ "", "" ]}" http://localhost:8081/artifactory/api/plugins/execute/modifyP2Urls
```

