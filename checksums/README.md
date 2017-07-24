Artifactory Checksums User Plugin
======================================

This plugin provides additional checksums support to Artifactory.

Features
--------

In addition to the natively supported MD5 and SHA-1, this plugin adds to Artifactory support to the following algorithms:

- SHA-256
- SHA-384
- SHA-512

Installation
------------

To install this plugin:

1. Place file `checksums.groovy` under the master Artifactory server `${ARTIFACTORY_HOME}/etc/plugins`.
2. Verify in the `${ARTIFACTORY_HOME}/logs/artifactory.log` that the plugin loaded correctly.

Execution
---------

To retrieve the checksum values of an artifact, use the appropriate file extension when requesting the artifact:

- SHA-256: `.sha256`
- SHA-384: `.sha384`
- SHA-512: `.sha512`

The snippet below shows how to retrieve an artifact SHA-512 checksum using the 
[Retrieve Artifact](https://www.jfrog.com/confluence/display/RTF/Artifactory+REST+API#ArtifactoryRESTAPI-RetrieveArtifact) 
method from the Artifactory REST API:

```
curl -X GET -v -u user:password "http://localhost:8088/artifactory/<REPO_KEY>/<PATH_TO_ARTIFACT>.sha512"`
```
