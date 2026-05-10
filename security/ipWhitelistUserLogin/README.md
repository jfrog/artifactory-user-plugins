Artifactory IP-based User Login Whitelisting
=====================================================

This plugin allows to limit user login for specific users to a specific list of IP addresses.

1. If a user is not listed in the configuration, the login is not restricted, and it is the default as it would be without using this plugin
2. The configuration allows to specify for a specific username the allowed IP address and the Group to be assigned when logging in.
3. If a user is listed in the configuration, all given IP addresses are checked. This is a simple 'starts With' check. 
   1. If the IP address is not included in the list, the User Login is prevented
   2. If the IP address is in the list, the User gets assigned the given Group (if it exists), and can log in

## Configuration

The configuration [ipWhitelistUserLogin.json](ipWhitelistUserLogin.json) is used to configure
the plugin.

The `assignGroup` option is optional, but should be used for security reasons!

I.e., use this specific group to define permissions for the user, do not use the username directly.
If the plugin is somehow disabled for whatever reason, the user may log in from any IP and automatically has
the given rights.
If you use a dedicated group, and this Plugin is somehow disabled, the user is not assigned to the group,
and therefore does not have any rights.


Based on the idea given here:
https://www.jfrog.com/jira/browse/RTFACT-9157

Installation
------------

To install this plugin:

1. Place the configuration file 
      `ipWhitelistUserLogin.json` file in the
      `${ARTIFACTORY_HOME}/etc/plugins` directory and adapt it to your needs.
2. Create any groups you configure in the config, and adapt the permissions.
3. Place the `ipWhitelistUserLogin.groovy` file in the
   `${ARTIFACTORY_HOME}/etc/plugins` directory.
4. You are done. Verify that the plugin properly works by trying to log in from a non-allowed IP.