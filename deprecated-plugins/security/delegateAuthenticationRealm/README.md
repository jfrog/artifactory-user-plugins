Artifactory Delegate Authentication Realm User Plugin
=====================================================

This plugin allows authentication to be delegated from one Artifactory instance
to another, and for user information to be synchronized between the instances.
Synchronization is one-way only. A possible configuration would be to specify
a 'central' instance, where the authoritative user details are stored, and one
or more other instances, which pull user information from the central. In this
setup, always make user account changes directly to the central instance, since
otherwise, the changes are likely to revert.

This plugin depends on the [getCurrentUserDetails][] user plugin.

Installation
------------

Before installing, be sure to choose an instance to designate as the 'central',
from which user details can be queried by the other instances.

To install this plugin:

1. On the central instance, place the `getCurrentUserDetails.groovy` file (from
   the [getCurrentUserDetails][] user plugin) in the
   `${ARTIFACTORY_HOME}/etc/plugins` directory.
2. Edit the `delegateAuthenticationRealm.groovy` file: the
   `centralAuthenticatorArtifactory` variable must be set to the URL of the
   central instance.
3. On any non-central instances, place the modified
   `delegateAuthenticationRealm.groovy` file in the
   `${ARTIFACTORY_HOME}/etc/plugins` directory.

Now, each time a user logs into a non-central instance, Artifactory will attempt
to delegate the login to the central instance, and update the user's details as
well.

[getCurrentUserDetails]: https://github.com/JFrogDev/artifactory-user-plugins/tree/master/security/getCurrentUserDetails
