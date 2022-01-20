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

For example:

``` json
dockerRepos = ["example-docker-local", "example-docker-local-2"]
byDownloadDate = false
```

Usage
-----

Cleanup policies are specified as labels on the Docker image. Currently, this
plugin supports the following policies:

- `maxDays`: The maximum number of days a Docker image can exist in an
  Artifactory repository. Any images older than this will be deleted.
    * when `byDownloadDate=true`: images downloaded or updated within last `maxDays` will
      be preserved
- `maxCount`: The maximum number of versions of a particular image which should
  exist. For example, if there are 10 versions of a Docker image and `maxCount`
  is set to 6, the oldest 4 versions of the image will be deleted.
    * when `byDownloadDate=true`: image age will be determined by first checking
     the _Last Downloaded Date_ and _Modification Date_ will be checked only when this image has never
     been downloaded.

To set these labels for an image, add them to the Dockerfile before building:

``` dockerfile
LABEL com.jfrog.artifactory.retention.maxCount="10"
LABEL com.jfrog.artifactory.retention.maxDays="7"
```

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
