Artifactory Security Replication User Plugin
============================================

This plugin continuously synchronizes security data between multiple Artifactory
instances. So, any change made to any instance's security data will be
automatically replicated to the others. This allows, for example, a user to
regenerate an API key from one instance, and then use the new key from another.

This plugin is currently in a beta stage of development. It is still rather
fragile, and probably not yet fit for most use cases. It is recommended that you
do not use this plugin in a production environment in its current state.

Details
-------

The plugin runs on a scheduled job with a configurable timer. The job polls for
changes in the security data, and syncs those changes to the other instances.
For this reason, changes made may not synchronize immediately.

Replicated security data includes all users, groups, and permissions, and all
associated data, including user passwords, API keys, OAuth keys, etc. It is
possible to restrict this to only users, or only users and groups, rather than
all three. This can be done in the configuration file, as specified below.

Configuration
-------------

The plugin is designed to run under a full mesh topology. Note that the topology
used for the plugin does not necessarily need to match the one used for artifact
replication.

Configuration is done via the configuration file, `securityReplication.json`.
This file takes the following information:

- `authorization`: The password (or, preferrably, api key) and username of an
  **admin user**. This user will be used to communicate between all instances in the
  mesh and needs to be present on all instances of the mesh.
- `urls`: A list of urls of all the instances in the mesh. This list is in order
  of priority: in the event of a data conflict (say, if the same user's api key
  was regenerated on two different instances at the same time), the data from
  the instance that is higher on the list will be used.
- `whoami`: The url of the current local instance. This should match one of the
  urls on the `urls` list.
- `filter`: How much data to synchronize. If this is `1`, only synchronize
  users. If it's `2`, synchronize users and groups. If `3`, synchronize users,
  groups, and permissions.
- `cron_job`: The [quartz][] style cron expression describing how often
  synchronization occurs. Changes to this will not take effect until after the
  plugin is reloaded or Artifactory is restarted.
- `startTime`: A timestamp representing when the artifactory instance was
  started. This is used by the plugin to detect crashes and restarts. This is
  used internally by the plugin, and is not a configuration option. It should
  not be modified.

[quartz]: http://www.quartz-scheduler.org/documentation/quartz-2.x/tutorials/crontrigger.html

Setup
-----

Ensure you have an admin user that has the same login and password across your entire mesh.

Write a configuration file using the fields above. For example:

``` json
{
  "securityReplication": {
    "authorization": {
      "username": "admin",
      "password": "password"
    },
    "filter": 1,
    "cron_job": "0 0 0/1 * * ?",
    "startTime": 0,
    "urls": [
      "http://localhost:8088/artifactory",
      "http://localhost:8090/artifactory",
      "http://localhost:8092/artifactory"
    ],
    "whoami": "http://localhost:8088/artifactory"
  }
}
```

Save this file as `$ARTIFACTORY_HOME/etc/plugins/securityReplication.json` on
one of the Artifactory instances (make sure the instance is the one specified by
the `whoami` field). Also, save the plugin file in the same directory (so,
`$ARTIFACTORY_HOME/etc/plugins/securityReplication.groovy`).

Refresh the plugin list:

``` shell
curl -uadmin:password -XPOST http://localhost:8088/artifactory/api/plugins/reload
```

Then, call the plugin's `distSecRep` endpoint. This endpoint will deploy the
plugin to all other instances in the `urls` list, and set up the config file on
each of those instances:

``` shell
curl -uadmin:password -XPOST http://localhost:8088/artifactory/api/plugins/execute/distSecRep
```

At this point, any HA instances may need their plugin lists refreshed manually,
for each node in the instance.

Now, the plugin should be properly configured and running on all instances.  You can easily confirm by temporarily [enabling the debug log](https://github.com/JFrogDev/artifactory-user-plugins/tree/master/security/securityReplication#logging) and confirming in `artifactory.log`.

### Optional Setup Tasks
Once the plugin has propagated across your mesh and is working correctly, consider creating a dedicated user for security replication.  Ensure this user has admin privileges, and generate an API key for this user. The plugin will ensure the user and its API key are replicated across the mesh.

You may then use this user's login and API key in your `securityReplication.json`:

``` json
{
  "securityReplication": {
    "authorization": {
      "username": "<REPLICATION USER>",
      "password": "<API KEY>"
    },
```

Edit `securityReplication.json` on your master instance, then call the distribution API endpoint to distribute the new `.json` file to the mesh:

``` shell
curl -u<REPLICATION USER>:<API KEY> -XPOST http://localhost:8088/artifactory/api/plugins/execute/distSecRep
```

Now your security replication is using an API key instead of a plaintext password.


Removing the Plugin
-------------------

To remove an instance A from the mesh:

1. Edit the `securityReplication.json` file on another instance B, and delete the url for instance A from the urls list.
1. Call the `distSecRep` REST endpoint on instance B, which will propagate the updated configuration to all the other instances.
1. Delete the plugin `groovy` and related `json` files from the filesystem on instance A.
1. Restart instance A (due to a limitation in Groovy, calling the `plugins/reload` REST endpoint does not unload deleted plugins, so a full restart of Artifactory is required).


FAQ
---

> Will users in Cluster B be replicated to Cluster A if Cluster A doesn’t have the user and Cluster A has the highest precedence?

Yes, changes on any cluster will be replicated to any other cluster, regardless of precedence. Precedence only comes into play when there are direct conflicts in data. For example, if the API key for a particular user is regenerated on both clusters at the same time, the API key in Cluster A will take precedence. Or, if a user is created in Cluster A, and a different user with exactly the same name is created in Cluster B at the same time, the user in Cluster A will be replicated, and will overwrite the user in Cluster B.



Logging
-------
To enable logging, amend this to the end of `logback.xml`
``` xml
<logger name="securityReplication">
    <level value="debug"/>
</logger>
```
Be careful; the logging is rather verbose. You might want to enable it temporarily.

Issues and Limitations
----------------------

- There are currently no locks on whether an instance is in the middle of
  replication. This means that the instance might be updated by two different
  jobs at once. This will never happen unless something else has gone wrong (the
  instance is part of two meshes at once, etc), but the plugin still needs to be
  resistant to this problem. This will be fixed in the future.
- The plugin assumes a full-mesh topology. It is possible to use a single-star
  topology instead, with some manual modification of the config files, but other
  topologies are not supported. Note that the topology used by this plugin need
  not necessarily match that used for artifact replication; you can use, say, a
  multi-star topology for artifact replication and a full-mesh for security.
- The plugin uses a single admin user to access all instances in the mesh, and
  does not yet support different users for different instances. This shouldn't
  be too much of a problem, considering the plugin ensures that all users exist
  with the same properties on all instances, but it still might be preferred to
  have this option available.
- When upgrading the plugin itself, do not refresh the plugin lists while the
  plugin's sync job is running. Doing this can cause the plugin to stop working
  entirely. If this happens, Artifactory must be restarted.
