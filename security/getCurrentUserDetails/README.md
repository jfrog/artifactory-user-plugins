Artifactory Get Current Users details Plugin


This plugin provides you the ability to get all users information from one artifactory instance to another artifactory instance. This plugin is required to use with security/delegateAuthenticationRealm/delegateAuthenticationRealm.groovy plugin.

This user plugin need to install on an instance with the users themselves (delegating to the first instance), which will connect to artifactory using delegateAuthenticationRealm plugin.

Adding to Artifactory


This plugin need to be added to the $ARTIFACTORY_HOME/etc/plugins directory.


