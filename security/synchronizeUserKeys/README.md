Artifactory Synchronize User Keys User Plugin
========================================

This plugin syncs the user keys between two instances of Artifactory. When any file called `settings.xml` is downloaded from the master instance, the public and private user keys are copied to the slave instance. The plugin must be installed on both instances of Artifactory.The public and private keys are available in Artifactory under `Admin -> Advanced -> Security Descriptor`.

Modify the `synchronizeUserKeys.groovy` on line 41 and 42 to 
`if (port == {MASTER_PORT}) 'http://{ARTIFACTORY_URL}:{SLAVE_PORT}/artifactory'
    else 'http://{ARTIFACTORY_URL}:{MASTER_PORT}/artifactory'`