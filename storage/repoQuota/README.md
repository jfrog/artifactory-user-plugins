Artifactory Storage Quota User Plugin
=============================================

*This plugin is currently being tested for Artifactory 5.x releases.*

This plugin is used to limit storage under a repository path.

Installation
------------

To install this plugin:

1. Place this script under the master Artifactory server
   `${ARTIFACTORY_HOME}/etc/plugins`.
2. Verify in the `${ARTIFACTORY_HOME}/logs/artifactory.log` that the plugin loaded
   correctly.

Features
--------

- Ability for an administrator to set property named `repository.path.quota` on any existing repository path to the number of bytes to limit for artifacts stored under the repository path. Once the limit is met all future put requests to store artifacts under the repository path are rejected.

### Execution ###
This plugin is a storage beforeCreate execution. It also is a storage beforePropertyCreate and beforePropertyDelete execution to restrict property `repository.path.quota` to administrators.
