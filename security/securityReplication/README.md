Artifactory Security Replication User Plugin
============================================

**&ast;&ast;&ast; *Deprecation NOTICE* &ast;&ast;&ast;**  
*With the release of the [Access Federation](https://www.jfrog.com/confluence/display/RTF/Access+Federation) feature of the Enterprise Plus Package of JFrog Artifactory, the Security Replication plugin has been deprecated.  Accordingly, support for Security Replication is not being carried forward for versions of JFrog Artifactory 6.x and beyond.  Please contact your account manager or JFrog Support if you are using this plugin or if you need its capability.*

**&ast;&ast;&ast; IMPORTANT &ast;&ast;&ast;**  
*If you are upgrading to Artifactory 5.6 or higher, please read the upgrade
instructions [below](#upgrading). Failure to do so may result in destruction of
password data.*

This plugin continuously synchronizes security data between multiple Artifactory
instances. So, any change made to any instance's security data will be
automatically replicated to the others. This allows, for example, a user to
regenerate an API key from one instance, and then use the new key from another.

Details
-------

The plugin runs on a scheduled job with a configurable timer. The job polls for
changes in the security data, and syncs those changes to the other instances.
For this reason, changes made may not synchronize immediately.

Replicated security data includes all users, groups, and permissions, and all
associated data, including user passwords, API keys, OAuth keys, etc (Note that
JFrog Access tokens are not replicated). It is possible to restrict this to only
users, or only users and groups, rather than all three. This can be done in the
configuration file, as specified below.

Upgrading
---------

If you are already using this plugin, and you plan to upgrade your Artifactory
instances from version 5.5 or older to version 5.6 or newer, please read this
section carefully.

Changes in the security subsystems in Artifactory 5.6 have caused password data
to be incompatible with older versions. Because of this, upgrading to
Artifactory 5.6 or newer before updating this plugin can lead to **destruction
of all password data**.

The following steps should be taken when upgrading:

1. **Update this plugin before upgrading any Artifactory instances.** The plugin
   must be updated on ALL instances before any further steps are taken. This can
   be done easily by updating the plugin on one instance, and then calling that
   instance's `distSecRep` endpoint, as described in the [setup](#setup) section
   below. This will deploy the updated plugin to all other instances in the
   mesh.
2. **Ensure the plugin is updated on all instances.** It is possible that the
   plugin might not update on some nodes, and may need to be updated or reloaded
   manually. To determine whether/which nodes require intervention, use the
   `secRepValidate` endpoint, as described in the
   [setup validation](#setup-validation) section below. Do not upgrade any
   Artifactory nodes until you see a successful validation.
3. Now that you are certain that all nodes are running the latest version of
   this plugin, the Artifactory instances can be upgraded. The latest version of
   the plugin ensures that replication can only occur if all Artifactory
   instances are compatible versions: replication will not trigger after the
   first Artifactory node is upgraded, and it will not resume until all nodes in
   the mesh have been upgraded. Therefore, it is recommended that all nodes are
   upgraded in a timely manner, to minimize the amount of time this plugin is
   disabled.
4. Once all nodes in the mesh are upgraded, it is recommended to check whether
   replication has started again. If it hasn't, double check that all nodes in
   the mesh are the correct version and that the plugin is still active.

- When upgrading an HA cluster to Artifactory 5.6, do not modify any security
  data (such as adding or modifying groups, users or security permissions)
  until all nodes in the cluster are upgraded, as such changes may be lost.
- When upgrading a securityReplication mesh, it is recommended to not introduce
  new Artifactory instances to the mesh until all existing instances are
  upgraded and replication has resumed. It is also recommended to ensure that
  replication runs correctly before beginning the upgrade process. This ensures
  that all nodes are in a fully synchronized state when replication resumes.
- Note that issuing a plugin reload is not recommended while this plugin's sync
  job is running, as it may cause the plugin to stop working entirely. If this
  happens, restarting the Artifactory instance should fix it.

Starting with Artifactory 5.6, this plugin is not forward-compatible with newer
versions of Artifactory by default, and any upgrade to Artifactory will require
an update to this plugin, or else replication will not run. This is a safety
measure to prevent accidental data loss, in case of future incompatibilities
like the changes in Artifactory 5.6. Newer versions of the plugin will continue
to work on older versions of Artifactory. You may disable this behavior by
adding a `"safety": "off"` entry to the configuration file (described
[below](#configuration)), but this is not recommended. In either case, it is
recommended to always check for updates [here][] before upgrading Artifactory.

[here]: https://github.com/JFrogDev/artifactory-user-plugins/tree/master/security/securityReplication

Configuration
-------------

The plugin is designed to run under a full mesh topology. Note that the topology
used for the plugin does not necessarily need to match the one used for artifact
replication.

Configuration is done via the configuration file, `securityReplication.json`.
This file takes the following information:

- `authorization`: The password (or, preferrably, api key) and username of an
  **admin user**. This user will be used to communicate between all instances in
  the mesh and needs to be present on all instances of the mesh.
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
- `safety`: By default, replication will not run when the Artifactory version is
  ahead of the plugin version. This means that each time Artifactory is upgraded,
  the plugin must also be updated. This is a safety measure to prevent
  accidental data loss due to incompatibilities between Artifactory and
  securityReplication. You may disable this behavior by including a `safety`
  entry in the configuration file with a value of `"off"`, but this is not
  recommended.

[quartz]: http://www.quartz-scheduler.org/documentation/quartz-2.x/tutorials/crontrigger.html

Setup
-----

Ensure you have an admin user that has the same login and password across your
entire mesh, and that password encryption policy in the general security settings of the security configuration page of the admin tab is set to 'OPTIONAL'.

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
    "urls": [
      "http://localhost:8088/artifactory",
      "http://localhost:8090/artifactory",
      "http://localhost:8092/artifactory"
    ],
    "whoami": "http://localhost:8088/artifactory",
    "safety": true
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

If possible, it may be best to call `distSecRep` directly on the HA node with
the new config file, rather than going through the load balancer. This ensures
that the correct config is distributed properly.

At this point, any HA instances may need their plugin lists refreshed manually,
for each node in the instance.

Now, the plugin should be properly configured and running on all instances. You
can easily confirm by temporarily [enabling the debug log][log] and confirming
in `artifactory.log`, and/or by running the validation endpoint, as described
below.

[log]: https://github.com/JFrogDev/artifactory-user-plugins/tree/master/security/securityReplication#logging

### Setup Validation ###

In some situations, especially in HA clusters, some nodes may not reload their
plugin lists or deploy plugin configuration files properly. In these cases, the
`distSecRep` endpoint may not be enough, and manual intervention may be
required. To easily identify and rectify these cases, and to ensure proper
deployment of this plugin in general, the `secRepValidate` endpoint can be used:

``` shell
curl -uadmin:password -XGET http://localhost:8088/artifactory/api/plugins/execute/secRepValidate
```

This endpoint checks every node in the mesh and ensures that they all have
correct versions and configurations. It diagnoses any irregularities and
recommends solutions. If this happens, you may need to manually deploy the
plugin or configuration, or run the plugin reload API, on certain nodes. Always
run the validation endpoint again after fixing these issues, to ensure a
successful validation:

```
==== Success! All nodes synced with securityReplication version 5.6.2 ====
```

When updating the plugin, double check that the plugin's version number matches
the expected version. It is recommended to run this check any time the plugin is
updated, and any time the plugin configuration is changed, to ensure that all
nodes are aware of the changes.

### Optional Setup Tasks ###

Once the plugin has propagated across your mesh and is working correctly,
consider creating a dedicated user for security replication. Ensure this user
has admin privileges, and generate an API key for this user. The plugin will
ensure the user and its API key are replicated across the mesh.

You may then use this user's login and API key in your
`securityReplication.json`:

``` json
{
  "securityReplication": {
    "authorization": {
      "username": "<REPLICATION USER>",
      "password": "<API KEY>"
    },
```

Edit `securityReplication.json` on your master instance, then call the
distribution API endpoint to distribute the new `.json` file to the mesh:

``` shell
curl -u<REPLICATION USER>:<API KEY> -XPOST http://localhost:8088/artifactory/api/plugins/execute/distSecRep
```

Now your security replication is using an API key instead of a plaintext
password, and you may now set your password encryption policy in the general security settings of the security configuration page of the admin tab to 'REQUIRED'

Removing the Plugin
-------------------

To remove an instance A from the mesh:

1. Edit the `securityReplication.json` file on another instance B, and delete
   the url for instance A from the urls list.
2. Call the `distSecRep` REST endpoint on instance B, which will propagate the
   updated configuration to all the other instances.
3. Delete the plugin `groovy` and related `json` files from the filesystem on
   instance A.
4. Restart instance A (due to a limitation in Groovy, calling the
   `plugins/reload` REST endpoint does not unload deleted plugins, so a full
   restart of Artifactory is required).
5. Call the `secRepValidate` REST endpoint on instance B, to ensure that the new
   `json` file was distributed and loaded correctly.

FAQ
---

> Will users in Cluster B be replicated to Cluster A if Cluster A doesn't have
> the user and Cluster A has the highest precedence?

Yes, changes on any cluster will be replicated to any other cluster, regardless
of precedence. Precedence only comes into play when there are direct conflicts
in data. For example, if the API key for a particular user is regenerated on
both clusters at the same time, the API key in Cluster A will take precedence.
Or, if a user is created in Cluster A, and a different user with exactly the
same name is created in Cluster B at the same time, the user in Cluster A will
be replicated, and will overwrite the user in Cluster B.

Logging
-------

To enable logging, append this to the end of `logback.xml`

``` xml
<logger name="securityReplication">
    <level value="debug"/>
</logger>
```

Be careful; the logging is rather verbose. You might want to enable it
temporarily.

Issues and Limitations
----------------------

- There are currently no locks on whether an instance is in the middle of
  replication. This means that the instance might be updated by two different
  jobs at once. This will never happen unless something else has gone wrong (the
  instance is part of two meshes at once, etc), but the plugin still needs to be
  resistant to this problem. This will be fixed in the future.
- The plugin assumes a full-mesh topology, and other topologies are not
  supported. Note that the topology used by this plugin need not necessarily
  match that used for artifact replication; you can use, say, a multi-star
  topology for artifact replication and a full-mesh for security.
- The plugin uses a single admin user to access all instances in the mesh, and
  does not yet support different users for different instances. This shouldn't
  be too much of a problem, considering the plugin ensures that all users exist
  with the same properties on all instances, but it still might be preferred to
  have this option available.
- If, for example, a permission target refers to a repository "test-repo", and
  this repository exists on site A but not on site B, the permission will still
  be replicated, and will simply refer to the nonexistant repository. If a
  repository "test-repo" was later created on site B, the permission should now
  grant privileges on this new repository. However, if "test-repo" is a remote
  repository, the permission will not be able to grant privileges, because it
  refers to "test-repo" and not "test-repo-cache" (all remote privileges are
  granted on remote caches). To fix this, the permission must be edited and
  re-saved, even if no changes are made to it; this triggers Artifactory to fix
  the repository name automatically. This issue does not occur if the repository
  already exists before the permission is first replicated.
- When upgrading the plugin itself, do not refresh the plugin lists while the
  plugin's sync job is running. Doing this can cause the plugin to stop working
  entirely. If this happens, Artifactory must be restarted.
