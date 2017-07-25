Artifactory Expires Package Metadata User Plugin
==============================================

This plugin expires files called Packages.gz when they are requested.

Installation
------------

To install this plugin:

1. Place this script under the master Artifactory server `${ARTIFACTORY_HOME}/etc/plugins`.
2. Verify in the `${ARTIFACTORY_HOME}/logs/artifactory.log` that the plugin loaded correctly.

Features
--------

This plugin runs every time a download request is received. It will force a check for expiry when:

- Artifact belongs to a remote repository
- Artifact local cache is older than 30 minutes
- Artifact name ends with `PACKAGES.gz` or `Packages.gz`

The expiry mechanism may force the artifact to be downloaded again from the remote repository. This can fix issues with CRAN metadata sync and with Debian metadata. 

Execution
---------

This plugin is automatically executed every time a download request is received by the Artifactory instance.
