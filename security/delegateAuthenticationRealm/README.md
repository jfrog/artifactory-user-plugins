Artifactory Get Current Users details Plugin


This plugin provides you the ability to get all users information from one artifactory instance to another artifactory instance. This plugin is required to use with security/getCurrentUserDetails/getCurrentUserDetails.groovy plugin.

This user plugin need to install on an instance, , which need to request user details from the second instance by providing artifactory connection details in this plugin.

For example, We have two artifactory instances artifactory-1 (port:8093) and artifactory-2 (port:8094). artifactory-1 is the instance where all users and passwords exist and we would like those user to use same credentials to login into artifactory-2. You will install getCurrentUserDetails plugin on artifactory-1 and install delegateAuthenticationRealm on artifactory-2. Once all users exist in artifactory-1 then you can use the same users credentials to login into artifactory-2. Once a user log into artifactory-2 then those users will be created in artifactory-2.

Adding to Artifactory


This plugin need to be added to the $ARTIFACTORY_HOME/etc/plugins directory.
