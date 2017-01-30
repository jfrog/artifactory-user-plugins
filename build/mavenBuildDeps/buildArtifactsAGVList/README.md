Artifactory User Plugin to create list of AGV (artifactId, groupId, Version) for all artifacts of build with their buildDependencies.
A REST executable artifact's AGV List builder.

This plugin promotes a build and it's dependent builds. It does the following:

First It will get list of artifacts from parent build and all its dependent builds. Then It will search for AGV (artifactId, groupId, Version) of  all artifacts by checksum from artifactory from created artifact list. After successfully getting AGV detail for all artifacts it will return AGV list as Json output.
Notes: Requires Artifactory Pro.

Parameters:
buildName - Name of build to promote.
buildNumber - Number of build to promote.
buildStartTime - StartTime of build to promote (optional - in case you have mutliple version of same build.)

For example:

```
curl -H "Content-Type:application/json" -X POST  -uadmin:password "http://localhost:8080/artifactory/api/plugins/execute/MavenDep?params=buildName=Generic|buildNumber=28"
```


Sample Output:
```
[{"groupId":"com.example.maven-samples","artifactId":"single-module-project","version":"1.0-20160331.163207-1"},{"groupId":"com.example.maven-samples","artifactId":"server","version":"1.0-20160331.163207-1"},{"groupId":"com.example.maven-samples","artifactId":"webapp","version":"1.0-20160331.163207-1"},{"groupId":"com.example.maven-samples","artifactId":"parent","version":"1.0-20160329.212249-11"},{"groupId":"com.example.maven-samples","artifactId":"multi-module-parent","version":"1.0-20160329.213557-14"},{"groupId":"com.mkyong","artifactId":"spring3-mvc-maven-xml-hello-world","version":"1.0-20160331.163152-19"},{"groupId":"org.jfrog.test","artifactId":"multi2","version":"3.3"},{"groupId":"org.jfrog.test","artifactId":"multi","version":"3.3"},{"groupId":"org.jfrog.test","artifactId":"multi1","version":"3.3"},{"groupId":"org.jfrog.test","artifactId":"multi3","version":"3.3"},{"groupId":"org.jfrog.test","artifactId":"bintray-info","version":"3.3"},{"groupId":"org.jfrog.test","artifactId":"multi2","version":"3.7-20160329.213502-14"},{"groupId":"org.jfrog.test","artifactId":"multi1","version":"3.7-20160329.205725-10"},{"groupId":"org.jfrog.test","artifactId":"multi","version":"3.7-20160329.213915-15"},{"groupId":"org.jfrog.test","artifactId":"multi3","version":"3.7-20160331.163108-18"}]
```


Note: This plugin is only supported in Artifactory version 4.0 or Later.






