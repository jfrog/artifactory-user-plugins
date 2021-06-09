Artifactory Expires Files Metadata User Plugin
==============================================

This plugin expires files when they are requested according to file pattern and expire delay stored in properties file so that the files are redownloaded.

This plugin is supported for Generic repositories only.

This plugin is inspired from [Expires Packages Metadata User Plugin](https://github.com/jfrog/artifactory-user-plugins/tree/master/metadata/expirePackagesMetadata).

`expireFilesMetadata.json`
----------
The Json contains the properties for expiring delay for different repositories.

JSon file format is Map where:
- the key is the repository key of the remote repository where the following conf is applied
  - delay : (in seconds) expire delay to force the download of the Files
  - patterns : file patterns to apply the delay for the remote repository

Here is JSon File Sample:

```
{
    "repositories": {
        "msys2-remote": {
            "delay": 1800,
            "patterns": ["**/*.db", "**/*.sig"]
        }
    }
}
```

Installation
------------

To install this plugin:

1. Place _this script_ and _the properties file_ under the master Artifactory server `${ARTIFACTORY_HOME}/etc/plugins`.
2. Verify in the `${ARTIFACTORY_HOME}/logs/artifactory.log` that the plugin loaded correctly.

Features
--------

This plugin runs every time a download request is received. It will force a check for expiry when:

- Artifact belongs to a remote repository and its package type is generic
- Artifact local cache is older than the expire delay specified in the properties file
- Artifact name statisfying the pattern given in the properties file

The expiry mechanism may force the artifact to be downloaded again from the remote repository.
This can fix issues with MSYS2 db metadata files. Thus you need :
* create a remote repo (ex : msys2-remote)
* point the MSYS2 official repo : http://repo.msys2.org
* use a proxy if needed
* pattern files to refresh msys2 metadata are : _*.db_, _*.sig_ : JSon file should be

```
{
    "repositories": {
        "msys2-remote": {
            "delay": 1800,
            "patterns": ["**/*.db", "**/*.sig"]
        }
    }
}
```

It can also be used to support alpine remote repositories.  Use :
* create a remote repository pointed at the alpine repository (e.g. apk-remote)
* file patterns for alpine are APKINDEX.tar.gz : JSon file should be

```
{
    "repositories": {
        "apk-remote": {
            "delay":600,
            "patterns": ["**/APKINDEX.tar.gz"]
        }
    }
}
```

Execution
---------

This plugin is automatically executed every time a download request is received by the Artifactory instance.


expireFilesMetadataConfig
-------------------------

`expireFilesMetadataConfig` updates the current Expire Files Metadata configuration using the provided map object.

Possible parameters are :
- action : reset (if parameter not present or different from 'reset' value, action will 'add' ie configuration passed via json content will added)
- json content : expire configuration coded in JSon format. See 'expireFilesMetadata.json' section for details

Example

```
$ curl -u admin:password -X POST -H 'Content-Type: application/json' -d '{
>     "repositories": {
>         "generic-remote-msys2": {
>             "delay": 1800,
>             "patterns": ["**/*.db", "**/*.xz*", "**/*.sig"]
>         }
>     }
> }' 'http://localhost:8088/artifactory/api/plugins/execute/expireFilesMetadataConfig?params=action=reset'

```
