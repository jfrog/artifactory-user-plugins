Artifactory Build Artifacts AGV List User Plugin
================================================

This plugin retrieves AGV (artifact, group, version) coordinates for all
artifacts specified by a particular build, or any if its build dependencies.

Parameters
----------

- `"buildName"`: Name of the build to promote.
- `"buildNumber"`: Number of the build to promote.
- `"buildStartTime"`: Start time of the build to promote (optional - in case you
  have multiple version of the same build)

Execution
---------

This plugin is callable via a REST endpoint, and returns results as a JSON
string.

For example:

For Artifactory 4.x:
```shell
curl -X POST -uadmin:password "http://localhost:8080/artifactory/api/plugins/execute/MavenDep?params=buildName=Generic|buildNumber=28"
```

For Artifactory 5.x:
```shell
curl -X POST -uadmin:password "http://localhost:8080/artifactory/api/plugins/execute/MavenDep?params=buildName=Generic;buildNumber=28"
```

Sample Output:
```json
[
  {"groupId": "com.example.maven-samples", "artifactId": "single-module-project", "version": "1.0-20160331.163207-1"},
  {"groupId": "com.example.maven-samples", "artifactId": "server", "version": "1.0-20160331.163207-1"},
  {"groupId": "com.example.maven-samples", "artifactId": "webapp", "version": "1.0-20160331.163207-1"},
  {"groupId": "com.example.maven-samples", "artifactId": "parent", "version": "1.0-20160329.212249-11"},
  {"groupId": "com.example.maven-samples", "artifactId": "multi-module-parent", "version": "1.0-20160329.213557-14"},
  {"groupId": "com.mkyong", "artifactId": "spring3-mvc-maven-xml-hello-world", "version": "1.0-20160331.163152-19"},
  {"groupId": "org.jfrog.test", "artifactId": "multi2", "version": "3.3"},
  {"groupId": "org.jfrog.test", "artifactId": "multi", "version": "3.3"},
  {"groupId": "org.jfrog.test", "artifactId": "multi1", "version": "3.3"},
  {"groupId": "org.jfrog.test", "artifactId": "multi3", "version": "3.3"},
  {"groupId": "org.jfrog.test", "artifactId": "bintray-info", "version": "3.3"},
  {"groupId": "org.jfrog.test", "artifactId": "multi2", "version": "3.7-20160329.213502-14"},
  {"groupId": "org.jfrog.test", "artifactId": "multi1", "version": "3.7-20160329.205725-10"},
  {"groupId": "org.jfrog.test", "artifactId": "multi", "version": "3.7-20160329.213915-15"},
  {"groupId": "org.jfrog.test", "artifactId": "multi3", "version": "3.7-20160331.163108-18"}
]
```

PomWithDeps Maven Plugin
------------------------

A Maven plugin that utilizes this plugin can be found [here][1]. This Maven
plugin allows you to create a single global pom.xml file that contains all
artifacts present in a build or any build dependencies.

An example using this Maven plugin can be found [here][2].

[1]: https://github.com/JFrogDev/artifactory-user-plugins/tree/master/build/buildArtifactsAGVList/util/pomWithDeps-maven-plugin
[2]: https://github.com/JFrogDev/artifactory-user-plugins/tree/master/build/buildArtifactsAGVList/util/pomWithDeps-example
