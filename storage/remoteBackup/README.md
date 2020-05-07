Artifactory Remote Backup User Plugin
=====================================

This plugin automatically copies files from a remote cache to a local 'backup'
repository. This ensures that cached artifacts are still available, even after
they're removed from the cache. This plugin can also be used to copy from a
local repository to a different local repository.

Whenever an artifact is added to the cache, the plugin immediately copies the
artifact to the backup repository. The plugin will also backup the entire cache
via a cron job, or on demand via a REST endpoint.

Note that this plugin will not copy properties on folders, including Docker
image folders. Properties on artifacts are copied as expected.

Configuration
-------------

Configuration is done via the configuration file, `remoteBackup.json`. This file
is simply a JSON object of repository pairs. For example, if you'd like to
backup `repo-foo-remote` to `repo-foo-backup-local`, and also backup
`repo-bar-remote` to `repo-bar-backup-local`, your configuration would be:

``` json
{
    "repo-foo-remote-cache": "repo-foo-backup-local",
    "repo-bar-remote-cache": "repo-bar-backup-local"
}
```

Be sure to append `-cache` to the end of any remote repositories you're backing
up.

Usage
-----

The plugin will run automatically for all configured repositories when an
artifact is cached or when the cron job triggers. If you would like to run it
manually, use:

``` shell
curl -u admin:password -X POST 'http://localhost:8081/artifactory/api/plugins/execute/remoteBackup'
```

You can also specify a subset of repositories to backup (note that all specified
repositories must already be listed in the configuration file):

``` shell
curl -u admin:password -X POST 'http://localhost:8081/artifactory/api/plugins/execute/remoteBackup?params=repos=repo-foo-remote-cache,repo-bar-remote-cache'
```
