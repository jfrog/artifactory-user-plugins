Artifactory Build Promotion User Plugin to promote build with buildDependencies.
A REST executable build promotion.

This plugin promotes a build and it's dependent builds. It does the following:

First It will get list of dependent builds. Then It will promote all dependent builds one by one using Promote API of artifactory. After successfully promoting all dependent build it will promote master build itself.
Notes: Requires Artifactory Pro.

Parameters:
buildName - Name of build to promote.
buildNumber - Number of build to promote.
buildStartTime - StartTime of build to promote (optional - in case you have mutliple version of same build.)

ReqBody
```
{
 "status": "staged" // new build status (any string)
 "comment" : "Tested on all target platforms." // An optional comment describing the reason for promotion. Default: ""
 "ciUser": "builder" // The user that invoked promotion from the CI server
 "timestamp" : ISO8601 // the time the promotion command was received by Artifactory (It needs to be unique).
 "dryRun" : false // run without executing any operation in Artifactory, but get the results to check if the operation can succeed. Default: false
 "targetRepo" : "libs-release-local" // optional repository/List of Repository to move or copy the build's artifacts and/or dependencies
 "copy": false // whether to copy instead of move, when a target repository is specified. Default: false
 "artifacts" : true // whether to move/copy the build's artifacts. Default: true
 "dependencies" : true // whether to move/copy the build's dependencies. Default: false.
 "scopes" : [ "compile", "runtime" ] // an array of dependency scopes to include when "dependencies" is true
 "properties": { // a list of properties to attach to the build's artifacts (regardless if "targetRepo" is used).
     "components": ["c1","c3","c14"],
     "release-name": ["fb3-ga"]
 }
 "failFast": true // fail and abort the operation upon receiving an error. Default: true
}
```

For example:

```
curl -H "Content-Type:application/json" -X POST -d '{"status": "Staged","comment": "Tested on all target platforms.", "ciUser": "admin","dryRun": false,"targetRepo": { "maven-example": "master-repo","dep-build1": "dep-repo1", "dep-build2": "dep-repo1", "dep-build3": "dep-repo1" }, "copy": true, "artifacts": true, "dependencies": false, "failFast": true }' -uadmin:password "http://localhost:8080/artifactory/api/plugins/execute/promoteWithDeps?params=buildName=Generic|buildNumber=28"
```

ReqBody:
```
{
  "status": "Staged",
  "comment": "Tested on all target platforms.",
  "ciUser": "admin",
  "dryRun": false,
  "targetRepo": {
    "maven-example": "master-repo",
    "dep-build1": "dep-repo1",
    "dep-build2": "dep-repo1",
    "dep-build3": "dep-repo1"
  },
  "copy": true,
  "artifacts": true,
  "dependencies": false,
  "failFast": true
}
```

Note: This plugin is only supported in Artifactory version 4.0 or Later.
By default this plugin will add following two properties to all artifacts of child builds:
```
parent.buildName
parent.buildNumber
```
which will help to search all artifacts promoted by parent build.







