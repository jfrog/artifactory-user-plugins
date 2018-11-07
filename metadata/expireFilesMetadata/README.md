Artifactory Expires Files Metadata User Plugin
==============================================

This plugin expires files when they are requested according to file pattern and expire delay stored in properties file.

This plugin is inspired from [Expires Packages Metadata User Plugin](https://github.com/jfrog/artifactory-user-plugins/tree/master/metadata/expirePackagesMetadata).

* Properties file format is Map where :
    * the key is the repository key of the remote reposity
    * the value is array where :
        * the first value is the expire delay (in seconds)
        * the following values are file patterns to apply the expire delay for the remote repository 

* Properties File Sample

```
repositories = [
    "generic-remote-msys2":
        [1800,
            ["**/*.db", "**/*.xz*", "**/*.sig"]
        ]
    ]
```

Installation
------------

To install this plugin:

1. Place _this script_ and _the properties file_ under the master Artifactory server `${ARTIFACTORY_HOME}/etc/plugins`.
2. Verify in the `${ARTIFACTORY_HOME}/logs/artifactory.log` that the plugin loaded correctly.

Features
--------

This plugin runs every time a download request is received. It will force a check for expiry when:

- Artifact belongs to a remote repository
- Artifact local cache is older than the expire delay specified in the properties file
- Artifact name statisfying the pattern given in the properties file

The expiry mechanism may force the artifact to be downloaded again from the remote repository.
This can fix issues with CRAN metadata sync and with Debian metadata and for MSYS2 db metadata files. 

Execution
---------

This plugin is automatically executed every time a download request is received by the Artifactory instance.
