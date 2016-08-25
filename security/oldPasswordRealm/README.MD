#Artifactory support to old DESede encrypted password  Plugin

This plugin provides you the ability to temporally support DESede encrypted password as base64 encryption has been decrypted with 4.9.1 Artifactory version. As part of this plugin, you can identify all DESede encrypted user’s by looking into private key’s and if any private key’s start with ‘M’ then get those user id’s and their encrypted passwords. Add those user id’s and DESede encrypted passwords in users.json file. Once added to users.json file then Artifactory should still allow those users to login into Artifactory.

###You can use below curl command to verify the login using DESede password.

    curl  -I -utest4:{DESede}XTBBQeBllBafJBccuRMdMw==  "http://localhost:8080/artifactory/libs-release-local/multi3-4.0.war"

###Adding to Artifactory

This plugin need to be added to the $ARTIFACTORY_HOME/etc/plugins directory.

