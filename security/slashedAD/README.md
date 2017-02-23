Artifactory Slashed AD Realm User Plugin
========================================

*This plugin is currently only working in Artifactory 4.x. We are working on updating it to work in Artifactory 5.x.*

This plugin introduces an LDAP realm that allows for a login style similar to
the old ActiveDirectory "domain\name" format (or "slashed" format). Due to some
restrictions of the Artifactory realm plugin system, this plugin does not
actually support the slashed format, but it does allow a similar format that has
many of the same advantages.

This plugin should be used on an Artifactory instance that authenticates with
more than one LDAP server. If two users exist on two separate LDAP servers and
happen to have the same username, Artifactory will treat them as a single user,
which can cause confusion and is insecure. This plugin introduces a login style
that makes the LDAP server part of the username, which keeps accounts with the
same username separate.

To use the plugin, use <kbd>[domain]+[user]</kbd> as your username, where
`[user]` is your LDAP username, and `[domain]` is the name of the Artifactory
LDAP Setting for the server you want to authenticate with. For example, if your
username is `robertk` and the LDAP Setting is named `corporate`, you would log
in as <kbd>corporate+robertk</kbd>, and provide your LDAP password. This will
authenticate you with `corporate` as `robertk`, but it will log you into
Artifactory as `corporate+robertk`, ensuring that there is no overlap between
LDAP servers.
