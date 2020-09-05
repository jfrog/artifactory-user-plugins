Artifactory Clean Docker Images User Plugin
===========================================

This plugin is used to clean Docker repositories based on configured cleanup
policies.

Configuration
-------------

The `cleanDockerImages.json` file has the following fields:

- `repos`: A list of Docker repositories to clean. If a repo is not in
  this list, it will not be cleaned.
- `timeInterval`: The time interval to look back before deleting an artifact. Defaults to *1*.
- `timeUnit`: The unit of the time interval. Values of *year*, *month*, *day*, *hour* or *minute* are allowed. Defaults to *month*.
- `dryRun`:  If this parameter is passed, artifacts will not actually be deleted. Defaults to *false*.

For example, a combination of `timeInterval` equal to `14` and a `timeUnit` equal to `day`, will trigger deletion of any Docker image not downloaded in recent two weeks:

```json
{
    "repos": [
        "libs-releases-local"
    ],
    "timeUnit": "day",
    "timeInterval": 7,
    "dryRun": false
}
```

Usage
-----

You can trigger running of this cleanup script using API call:

```shell
curl -X POST -v -u admin:password "http://localhost:8080/artifactory/api/plugins/execute/cleanDockerImages"
```

Also you can use a Cron job to cleanup your unused Docker repositories automatically.
