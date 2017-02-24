Artifactory Get Users By Group User Plugin
==========================================

*This plugin is currently only working in Artifactory 4.x. We are working on updating it to work in Artifactory 5.x.*

This plugin provides you the ability to get all users in a specific group.

Adding to Artifactory
---------------------

This plugin need to be added to the `$ARTIFACTORY_HOME/etc/plugins` directory.

Executing
---------

To execute this plugin:

`curl -X GET -u{admin_user}:{password} "http://{ARTIFACTORY_URL}:{PORT}/artifactory/api/plugins/execute/getUsersByGroup?params=group={group_name}"`
