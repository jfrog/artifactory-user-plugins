Artifactory Backups Config User Plugin
======================================

Allows REST access to the backup configuration. This plugin exposes five
executions:

getBackupsList
--------------

`getBackupsList` returns a JSON list containing the keys of all the currently
configured backups.

For example:

```
$ curl -u admin:password 'http://localhost:8081/artifactory/api/plugins/execute/getBackupsList'
[
    "backup-daily",
    "backup-weekly",
    "newbackup"
]
```

getBackup
---------

`getBackup` returns a JSON representation of the current configuration of a
given backup. The key of the requested backup is passed as the parameter `key`.
The returned JSON string has the following fields:

- `key`: The backup's unique ID key.
- `enabled`: Whether this backup is enabled to occur.
- `dir`: The directory to back up files to.
- `cronExp`: A cron expression which determines the frequency of the backing up.
- `retentionPeriodHours`: The maximum number of hours to keep backups after
  they're created. If this value is zero or negative, backups are kept
  incrementally.
- `createArchive`: Whether to back up to a zip file. This is slow and CPU
  intensive, and is not available with incremental backups.
- `excludedRepositories`: A list of repositories that should not be backed up.
- `sendMailOnError`: Whether to send an email to admin users if an error occurs.
- `excludeBuilds`: Whether to exclude builds from the backup.
- `excludeNewRepositories`: Whether to add newly created repositories to the
  `excludedRepositories` list by default.

For example:

```
$ curl -u admin:password 'http://localhost:8081/artifactory/api/plugins/execute/getBackup?params=key=newbackup'
{
    "key": "newbackup",
    "enabled": true,
    "dir": "/path/to/backup/dir",
    "cronExp": "0 0 0 * * ?",
    "retentionPeriodHours": 168,
    "createArchive": false,
    "excludedRepositories": null,
    "sendMailOnError": true,
    "excludeBuilds": true,
    "excludeNewRepositories": false
}
```

deleteBackup
------------

`deleteBackup` deletes a backup from the Artifactory instance. The key of the
backup to delete is passed as the parameter `key`.

For example:

```
$ curl -u admin:password -X DELETE 'http://localhost:8081/artifactory/api/plugins/execute/deleteBackup?params=key=newbackup'
```

addBackup
---------

`addBackup` adds a new backup to the Artifactory instance. The backup to add is
defined by a JSON object sent in the request body, with the same schema used by
`getBackup`.

For example:

```
$ curl -u admin:password -X POST -H 'Content-Type: application/json' -d '{
> "key": "newbackup",
> "enabled": true,
> "dir": "/path/to/backup/dir",
> "cronExp": "0 0 0 * * ?",
> "retentionPeriodHours": 168,
> "createArchive": false,
> "excludedRepositories": null,
> "sendMailOnError": true,
> "excludeBuilds": true,
> "excludeNewRepositories": false
> }' 'http://localhost:8081/artifactory/api/plugins/execute/addBackup'
```

updateBackup
------------

`updateBackup` updates an existing backup. The key of the backup to modify is
passed as the parameter `key`. The modifications are defined by a JSON object
sent in the request body, with the same schema used by `getBackup` and
`addBackup`. Only the fields that should be modified need to be included in the
JSON representation, and all other fields will be preserved.

For example:

```
$ curl -u admin:password -X POST -H 'Content-Type: application/json' -d '{
> "cronExp": "* * * * * ?",
> "createArchive": true,
> "excludedRepositories": ["libs-release-local"]
> }' 'http://localhost:8081/artifactory/api/plugins/execute/updateBackup?params=key=newbackup'
```
