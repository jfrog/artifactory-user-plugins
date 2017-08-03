Artifactory Nexus Push User Plugin
==================================

*This plugin is currently being tested for Artifactory 5.x releases.*

Artifactory plugin for pushing artifacts to a Nexus Staging Repository (for
example, oss.sonatype.org).

1. Setup:
   - Place this script under `${ARTIFACTORY_HOME}/etc/plugins`.
   - Place profile file under `${ARTIFACTORY_HOME}/etc/plugins`.

     Profile file is a Java properties file and contains 3 mandatory parameters:
     `stagingUrl`, `stagingUsername`, and `stagingPassword`. The only optional
     parameter currently supported is a comma separated list of exclusions in
     the form of ant fileset patterns: files matched by these patterns won't be
     staged. Example for a local Nexus install with default credentials:

     ```
     stagingUrl=http://localhost:8081/nexus
     stagingUsername=admin
     stagingPassword=admin123
     # Comma separated exclusions (using Ant pattern format)
     exclusions=**/*.zip, **/*.tar.gz
     ```

2. Execute a POST request authenticated with an Artifactory admin user with the
   following parameters separated by pipes (`|` for Artifactory 4.x, and `;` for Artifactory 5.x. ):
   - `stagingProfile`: The name of the profile file (without the 'properties'
     extension). E.g. for a profile saved in
     `${ARTIFACTORY_HOME}/etc/plugins/nexusPush.properties`, the parameter will
     be `profile=nexusPush`.
   - Query parameters can be one of the following:
     * By directory: defined by parameter `dir`. The format of the parameter is
       `repo-key/relative-path`. This is the desired directory URL, without the
       base Artifactory URL. E.g.
       `dir=lib-release-local/org/spacecrafts/spaceship-new-rel/1.0`.
     * By build properties: any number of `property=value1,value2,valueN` pairs
       are allowed, where `property` is the full name of the Artifactory
       property (including the set name). All artifacts with all of these
       properties and values will be pushed. E.g.
       `build.name=spaceship-new-rel|build.number=143` for Artifactory 4.x, and `build.name=spaceship-new-rel;build.number=143` for Artifactory 5.x. .
   - `close`: whether or not the staging repository should be closed. Defaults
     to `true`.

3. Examples of the request using CURL:
   - Query by directory, upload only (without closing):
     `curl -X POST -v -u admin:password "http://localhost:8090/artifactory/api/plugins/execute/nexusPush?params=stagingProfile=nexusPush|close=false|dir=lib-release-local%2Forg%spacecrafts%2Fspaceship-new-rel%2F1.0"` for Artifactory 4.x, and `curl -X POST -v -u admin:password "http://localhost:8090/artifactory/api/plugins/execute/nexusPush?params=stagingProfile=nexusPush;close=false;dir=lib-release-local%2Forg%spacecrafts%2Fspaceship-new-rel%2F1.0"` for Artifactory 5.x. 
   - Query by properties:
     `curl -X POST -v -u admin:password "http://localhost:8090/artifactory/api/plugins/execute/nexusPush?params=stagingProfile=nexusPush|build.name=spaceship-new-rel|build.number=143"` for Artifactory 4.x, and `curl -X POST -v -u admin:password "http://localhost:8090/artifactory/api/plugins/execute/nexusPush?params=stagingProfile=nexusPush;build.name=spaceship-new-rel;build.number=143"` for Artifactory 5.x. 
