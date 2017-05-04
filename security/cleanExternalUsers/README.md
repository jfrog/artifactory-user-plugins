Artifactory Clean External Users User Plugin
============================================

This plugin is designed to delete users from Artifactory that have been
deactivated from your SSO server. If a user has been given access to their
Artifactory profile page, they may create an API key for their Artifactory user.
Since this bypasses SSO, such a user may still be able to log into Artifactory
after they're deactivated from the SSO server. This plugin deletes these users
from Artifactory, so an API key can't be used.

Configuration
-------------

Because of the wide range of SSO servers available, this plugin is designed to
be modified to support any of them. When using the plugin, the `usersToClean`
function should be reimplemented to suit your specific needs. This function
should reach out to your SSO server and retrieve a list of deactivated users.

The `usersToClean` function has the following properties:

- It takes one parameter, which is a `Map<>` containing the plugin's
  configuration. This can contain any useful options your specific
  implementation might require.
- It should return an `Iterable<String>`, containing the Artifactory usernames
  that should be deleted. The plugin will delete any user with one of these
  names. If a user in the list doesn't exist in Artifactory, the plugin will
  skip it.

The default sample implementation of `usersToClean` retrieves a set of users
from [Okta](https://www.okta.com/). If you use Okta, you need not change this
function.

The plugin has an optional configuration file `cleanExternalUsers.json`, which
contains the default configuration options. When reimplementing `usersToClean`,
any new options you may find useful can be added to this file. The only options
that are used outside of `usersToClean` are the following:

- `cronjob`: A [quartz cron expression][cron] describing how often to run the
  plugin automatically. If this is left out, the plugin will not run
  automatically, but can still be run manually via the REST API.
- `dryRun`: If this is `true`, log and count users that should be deleted, but
  don't actually delete any. This is `false` by default.

[cron]: http://www.quartz-scheduler.org/documentation/quartz-2.x/tutorials/crontrigger.html

The default Okta implementation of `usersToClean` uses the following
configuration options:

- `host`: The hostname of the Okta server. For example, `"orgname.okta.com"`.
- `apitoken`: The API token to use for authentication with the Okta server.

Execution
---------

The plugin can be run on a cron timer. To do this, a `cleanExternalUsers.json`
file must exist, as the values for all required options will be taken from this
file. This includes a cron expression describing how often the plugin is to run.

The plugin can also be run manually via the Artifactory REST API. If the
`cleanExternalUsers.json` file exists, it will hold the default values for any
options it specifies, but they can be overridden by passing parameters in the
REST call. For example, to manually execute a dry run:

``` shell
curl -uadmin:password -XPOST 'http://localhost:8081/artifactory/api/plugins/execute/cleanExternalUsers?params=dryRun=true'
```
