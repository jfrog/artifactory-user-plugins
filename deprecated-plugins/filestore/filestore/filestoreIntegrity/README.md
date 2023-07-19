Artifactory Filestore Integrity User Plugin
===========================================

Checks the integrity of the filestore. This plugin reveals discrepancies between
the Artifactory database and the filestore, such as artifacts with missing
binaries, and extra binaries that don't correspond to any artifacts. It is
designed to run quickly and efficiently, even for Artifactory instances with
large numbers of artifacts.

`filestoreIntegrity` returns a JSON object, which contains two lists:
- `missing`: A list of artifacts (repository path and sha1 checksum) that appear
  in the Artifactory database, but do not have corresponding files on the
  filesystem.
- `extra`: A list of files that appear on the filesystem, but do not have
  corresponding entries in the database.

For example:

```
$ curl -u admin:password 'http://localhost:8081/artifactory/api/plugins/execute/filestoreIntegrity'
{
    "missing": [
        {
            "repoPath": "ext-release-local:bcprov-jdk15on/bcprov-jdk15on/1.53/bcprov-jdk15on-1.53.jar",
            "sha1": "9d3def2fa5a0d2ed0c1146e9945df10d29eb4ccb"
        }
    ],
    "extra": [
        "31/thisFileShouldNotExist",
        "9c/9d3def2fa5a0d2ed0c1146e9945df10d29eb4ccb"
    ]
}
```

Due to its design, this plugin only works with Artifactory instances that use a
simple filesystem-based filestore (usually a local filesystem store, or an NFS
mount). If you wish to find broken binaries but are using a more complex
filestore, such as S3, HDFS, or a sharded store, you can use [this script][]
instead. Note that said script is not particularly fast or efficient, and may
not be suitable for use on large Artifactory instances.

[this script]: https://github.com/JFrogDev/artifactory-scripts/tree/master/filestoreIntegrity
