# CRAN Repository User Plugin

This plugin adds CRAN (The Comprehensive R Archive Network) repositories support to Artifactory.

## Features

This plugin allows the creation of local and remote CRAN repositories in Artifactory. 

Virtual repositories ARE NOT supported.

### Local Repositories

Using local repositories, users can host their own CRAN packages inside Artifactory.

To make the packages available to R clients, this plugin takes care of indexing the deployed CRAN packages accordingly to the CRAN repository specification. 

Besides that, when a package is deployed to the repository's root folder, the plugin identifies the package type and move it to the right location where the R client can find it. This layout enforcement feature is available for the following package types:

- Source packages: `.tar.gz` files
- MacOSX binary packages: `.tgz` files
- Windows binary packages: `.zip` files 

Users can also have a custom repository layout by deploying the package at any location other than the root folder.

All package metadata present in the `DESCRIPTION` file inside the package is also stored as artifact properties, so users can search and manipulate packages on Artifactory based on them. These metadata properties have the format `cran.<property_name>`.

The plugin also adds additional metadata about the indexing process to each terminal folder containing CRAN packages. These metadata are:

- `cran.indexing.status`: Indexing process status. Possible values are: *scheduled*, *running*, *done* or *failed*
- `cran.indexing.packages`: Number of packages indexed by the last successful execution
- `cran.indexing.time`: Time taken in milliseconds by the last successful execution
- `cran.indexing.last_execution`: End date of the last successful execution

### Remote Repositories

Using remote repositories, users can proxy and cache remote CRAN packages.

The remote repository index (`PACKAGES` file and compressed versions) local cache will expire every 10 minutes. After that time, Artifactory will fetch the remote repository index if available.

## Installation

To install this plugin:

1. Place file `cran.groovy` under the master Artifactory server `${ARTIFACTORY_HOME}/etc/plugins`.
2. Verify in the `${ARTIFACTORY_HOME}/logs/artifactory.log` that the plugin loaded correctly.

### Logging

To enable logging, add the following lines to `$ARTIFACTORY_HOME/etc/logback.xml` file. There is no need to restart Artifactory for this change to take effect:

```xml
<logger name="cran">
    <level value="info"/>
</logger>
```

## Usage

### Local repositories

To configure a CRAN local repository, create a generic local repository and set the property `cran` to any value at the repository root level. The repository will automatically start the metadata extraction, indexing and layout enforcement tasks to new packages deployed to it.

To manually request indexing of a repository's packages folder, the following API can be used:

*For Artifactory 4.x:*
```
curl -X POST -v -u user:password "http://localhost:8080/artifactory/api/plugins/execute/cranIndex?params=repoKey=cran-local|path=src/contrib"`
```

*For Artifactory 5.x:*
```
curl -X POST -v -u user:password "http://localhost:8080/artifactory/api/plugins/execute/cranIndex?params=repoKey=cran-local;path=src/contrib"`
```

### Remote repositories

No additional configuration is needed for remote repositories. Just create a generic remote repository and set the URL to the root of the remote repo (e.g. https://cran.r-project.org/)
