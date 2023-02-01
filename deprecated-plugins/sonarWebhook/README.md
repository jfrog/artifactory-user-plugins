Artifactory Sonar Webhook User Plugin
=====================================

This plugin adds a REST API endpoint that receives SonarQube webhooks. It
gathers Quality Gate results, and proceeds depending on the status:

- If the Quality Gate succeeds, the build will be promoted to a given target
  repository for staging.
- If the Quality Gate fails, the build will roll-back promotion.

Usage
-----

Create project on the Sonar server, with a webhook as follows:

```
http://admin:password@artifactory:8081/artifactory/api/plugins/execute/updateSonarTaskStatus?params=targetRepo=gradle-staging-local;sourceRepo=gradle-dev-local;rolledBackRepo=gradle-garbage-local
```

You can use the provided `jenkinsfile` to run a Gradle build with Sonarqube
analysis, and publish the build info to Artifactory with a Sonar Task ID.
