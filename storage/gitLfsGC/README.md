Artifactory Git LFS GC User Plugin
==================================

This plugin is designed to automatically delete unnecessary files from
Artifactory Git LFS repositories. It will delete all LFS files that aren't in
any of the current existing branches in the Git repository. This runs on a
configurable cron timer, and can also be run manually via an execution endpoint.

Before using the plugin, all repositories that the plugin should affect must be
configured in the `gitLfsGC.json` file. When the plugin runs, all of the
configured repositories will be cleaned. Alternatively, when the execution is
run, a subset of those repositories can be specified, and only those
repositories will be cleaned.

Be warned that when this plugin deletes files, they will no longer be
accessible, so you will not be able to checkout those files from old commits. Do
not use this plugin unless you are sure that those files will not be missed.

gitLfsGC.json
-------------

The configuration json has the following layout:
- `"cron"`: The cron expression describing how often the GC job should run.
  Optional, defaults to `"0 0 0 * * ?"` (every day at midnight).
- `"tmpdir"`: The directory to clone Git repositories into. Each repository
  configured for cleanup is cloned locally by the plugin, so that it can
  calculate which artifacts should be preserved. Optional, defaults to
  `"$TMPDIR/artifactoryGitLFSGCPlugin/"`, where `TMPDIR` is the system temp
  folder according to Java. This may be the actual temp folder, or it may have
  been set to somewhere else by Tomcat.
- `"repositories"`: A map of Git LFS repository names to configurations. All of
  these repositories are cleaned up when the cron job runs. When using the
  execution, no repositories can be cleaned other than the ones in this list.

Each repository configuration has the following layout:
- `"repourl"`: The url of the Git repository this Git LFS repository supports.
  This can be an `http:`, `https:`, `ssh:`, or `git:` url.
- `"username"`: When using `http:` or `https:`, this is the username or access
  token to use when authenticating. If the connection is not authenticated, this
  field should not be included.
- `"password"`: When using `http:`, `https:`, or `ssh:`, this is the password to
  use when authenticating. If the connection is not authenticated, this field
  should not be included.

For example:

``` json
{
  "cron": "0 0 0 * * ?",
  "tmpdir": "/tmp/artifactoryGitLFSGCPlugin/",
  "repositories": {
    "gitlfs-local": {
      "repourl": "http://domain/path/to/repository.git",
      "username": "user",
      "password": "passwd"
    }
  }
}
```

Usage
-----

To run the plugin via the execution endpoint:

``` shell
curl -X POST -u admin:password 'http://localhost:8081/artifactory/api/plugins/execute/gitLfsGC'
```

To run the plugin only on some of the configured repositories:

``` shell
curl -X POST -u admin:password 'http://localhost:8081/artifactory/api/plugins/execute/gitLfsGC?params=repos=repo1,repo2'
```

