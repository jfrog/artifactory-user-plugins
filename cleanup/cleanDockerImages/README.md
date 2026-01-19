Artifactory Clean Docker Images User Plugin
===========================================

This plugin is used to clean Docker repositories based on configured cleanup
policies.

Configuration
-------------

The `cleanDockerImages.properties` file has the following fields:

- `dockerRepos`: A list of Docker repositories to clean. If a repo is not in
  this list, it will not be cleaned.
- `byDownloadDate`: An optional boolean flag (true/false).
    * **false** (default): retention will take into account only **creation** date of the image
      (technically, its manifest file). This is the original behaviour.
    * **true**: identify images to remove by their **last download date** or failing that,
      last **update** date. This mode of operation has been inspired by the 'artifactCleanup'
      plugin.
- `globalMaxDays`: An optional global fallback value (integer) for `maxDays` retention policy.
  This value will be applied to all Docker images that don't have an explicit
  `com.jfrog.artifactory.retention.maxDays` label. Set to `null` or omit to disable global `maxDays` policy.
- `globalMaxCount`: An optional global fallback value (integer) for `maxCount` retention policy.
  This value will be applied to all Docker images that don't have an explicit
  `com.jfrog.artifactory.retention.maxCount` label. Set to `null` or omit to disable global `maxCount` policy.

For example:

``` groovy
dockerRepos = ["example-docker-local", "example-docker-local-2"]
byDownloadDate = false
globalMaxDays = 30
globalMaxCount = 5
```

This configuration will clean Docker images from the two specified repositories, using creation date
for retention checks. Images without explicit labels will be deleted if they are older than 30 days
or if there are more than 5 versions of the same image.

Usage
-----

Cleanup policies can be specified in two ways:

### 1. Image-Specific Labels (Priority)

Labels on individual Docker images take priority over global settings. Add them to the Dockerfile before building:

``` dockerfile
LABEL com.jfrog.artifactory.retention.maxCount="10"
LABEL com.jfrog.artifactory.retention.maxDays="7"
```

### 2. Global Fallback Values

For images without explicit labels, the plugin will use the global values configured in
`cleanDockerImages.properties` (see Configuration section above).

### Retention Policies

Currently, this plugin supports the following policies:

- `maxDays`: The maximum number of days a Docker image can exist in an
  Artifactory repository. Any images older than this will be deleted.
    * when `byDownloadDate=false` (default): images created within last `maxDays` will be preserved
    * when `byDownloadDate=true`: images downloaded or updated within last `maxDays` will
      be preserved
- `maxCount`: The maximum number of versions of a particular image which should
  exist. For example, if there are 10 versions of a Docker image and `maxCount`
  is set to 6, the oldest 4 versions of the image will be deleted.
    * when `byDownloadDate=false` (default): image age determined by creation date
    * when `byDownloadDate=true`: image age will be determined by first checking
     the _Last Downloaded Date_ and _Modification Date_ will be checked only when this image has never
     been downloaded.

**Note:** Image-specific labels always take precedence over global fallback values.

### Execution

When a Docker image is deployed, Artifactory will automatically create
properties reflecting each of its labels. These properties are read by the
plugin in order to decide on the cleanup policy for the image.

Cleanup can be triggered via a REST endpoint. For example:

``` shell
curl -XPOST -uadmin:password http://localhost:8081/artifactory/api/plugins/execute/cleanDockerImages
```

A dry run can also be triggered:

``` shell
curl -XPOST -uadmin:password "http://localhost:8081/artifactory/api/plugins/execute/cleanDockerImages?params=dryRun=true"
```

Or a special mode of operation to preserve last recently used images:
``` shell
curl -XPOST -uadmin:password "http://localhost:8081/artifactory/api/plugins/execute/cleanDockerImages?params=byDownloadDate=true"
```
