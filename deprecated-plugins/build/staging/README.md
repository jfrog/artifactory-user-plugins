Artifactory Build Staging Strategy User Plugin
==============================================

This plugin is used to define a set of staging strategies to be used by the build server during staging process.

Installation
------------

To install this plugin:

1. Place this script under the master Artifactory server `${ARTIFACTORY_HOME}/etc/plugins`.
2. Verify in the `${ARTIFACTORY_HOME}/logs/artifactory.log` that the plugin loaded correctly.
3. Create new staging strategies or customize the built-in ones accordingly to your needs by editing this script

Features
--------

The strategy provides the build server the following information:

- How the artifacts in the staged build should be versioned
- How the artifacts in the next integration build should be versioned
- Should the build server create a release branch/tag/stream in VCS and how it should be called
- To which repository in Artifactory the built artifacts should be submitted

The built-in strategies provided by this plugin are:

### simpleMaven ###

Considering a module which has 1.1.0 as its latest release, below is an output example:

```json
{
  "defaultModuleVersion" : {
    "moduleId" : "org.jfrog.test:my-project",
    "nextRelease" : "1.1.1",
    "nextDevelopment" : "1.1.1-SNAPSHOT"
  },
  "vcsConfig" : {
    "useReleaseBranch" : false,
    "createTag" : true,
    "tagUrlOrName" : "rel-1.1.1",
    "tagComment" : "[artifactory-release] Release version 1.1.1",
    "nextDevelopmentVersionComment" : "[artifactory-release] Next development version"
  },
  "promotionConfig" : {
    "targetRepository" : "staging-local",
    "comment" : "Staging Artifactory 1.1.1"
  }
}
```

### detailedMaven ###

Considering a build with one module which has 1.1.0 as its latest release, below is an output example:

```json
{
  "moduleVersionsMap" : {
    "org.jfrog.test:my-project" : {
      "moduleId" : "org.jfrog.test:my-project",
      "nextRelease" : "1.1.1-0",
      "nextDevelopment" : "1.1.1-SNAPSHOT"
    }
  },
  "vcsConfig" : {
    "useReleaseBranch" : false,
    "createTag" : true,
    "tagUrlOrName" : "multi-modules/tags/artifactory-1.1.1",
    "tagComment" : "[artifactory-release] Release version 1.1.1",
    "nextDevelopmentVersionComment" : "[artifactory-release] Next development version"
  },
  "promotionConfig" : {
    "targetRepository" : "libs-snapshot-local",
    "comment" : "Staging Artifactory 1.1.1"
  }
}
```

### gradle ###

Considering a build with one module which has 1.1.0 as its latest relase, below is an output example:

```json
{
  "moduleVersionsMap" : {
    "modulex" : {
      "moduleId" : "currentVersion",
      "nextRelease" : "1.1.0a",
      "nextDevelopment" : "1.1.1-SNAPSHOT"
    }
  },
  "vcsConfig" : {
    "useReleaseBranch" : false,
    "createTag" : true,
    "tagUrlOrName" : "gradle-multi-example-1.1.0a",
    "tagComment" : "[gradle-multi-example] Release version 1.1.0a",
    "nextDevelopmentVersionComment" : "[gradle-multi-example] Next development version"
  },
  "promotionConfig" : {
    "targetRepository" : "gradle-staging-local",
    "comment" : "Staging Artifactory 1.1.0a"
  }
}
```

Execution
---------

To execute this plugin use the [Retrieve Build Staging Strategy](https://www.jfrog.com/confluence/display/RTF/Artifactory+REST+API#ArtifactoryRESTAPI-RetrieveBuildStagingStrategy) method from Artifactory Rest API.

Example:

`curl -X GET -v -u user:password "http://localhost:8080/artifactory/api/plugins/build/staging/<STRATEGY_NAME>?buildName=<BUILD_NAME>"`
