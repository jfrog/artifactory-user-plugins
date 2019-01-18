# Artifactory Synchronize LDAP Group Plugin

**Notice:** *There have been reports of this plugin causing intermittent `401 Unauthorized` errors during login. This appears to be uncommon, but is more common when pulling from Docker repositories. Affected versions are Artifactory 6.5.13 and newer. We are in the process of investigating this issue.*

## Features

This plugin can be used to attach LDAP groups permission to any user. The permissions will be attached per session and the user will not become a constant member of the group.

## Examples

By default, the plugin uses an LDAP group settings named `il-users`. Assume we import an LDAP group named 'sales' and example user named 'shayy' belongs to this LDAP group. We give full permission to the imported group 'sales' for an example repo. Now Sign in user 'shayy' using any non-internal method. Upon sign in, 'shayy' will inherit all permission from group 'sales' but it's not a constant member of the group.


## Get Start
1. Download 'SynchornizeLDAPGroups' user plugin and place it under your $ARTIFACTORY_HOME/etc/plugins (or $CLUSTER_HOME/etc/plugins if you are using High Availability)
2. Replace "il-users" with the name of your LDAP group that has been imported to Artifactory. 
![LDAP Group Setting Name](https://user-images.githubusercontent.com/7900285/29577731-60cd7480-8721-11e7-815a-087edaff8715.png)
3. Import the group you want to sync to Artifactory and attach [permission target](https://www.jfrog.com/confluence/display/RTF/Managing+Permissions). You can verify the groups in groups tab.
![Groups](https://user-images.githubusercontent.com/7900285/29577730-60cd01bc-8721-11e7-9114-c086a1f193a1.png)
4. After adding the user plugin run the [plugin reload rest api](https://www.jfrog.com/confluence/display/RTF/Artifactory+REST+API#ArtifactoryRESTAPI-ReloadPlugins) or restart Artifactory.


## Verify the Sync

Inside `myrelms`, add the following line to the plugin for debugging purposes only: `log.debug "user " + username+" have the following groups "+ groups`

Add the following logging to the $ARTIFACTORY_HOME/etc/logback.xml : (no need for restart) 

```
  <logger name="synchronizeLdapGroups">
      <level value="debug"/>
  </logger>
```

If you go to the artifactory.log you will be able to see that actually, although the UI doesn't show it, the debug line we added will show you that the user is part of the groups as he has on his LDAP server. In case you try to use this user with a permission that he has on the LDAP server, it will succeed as the plugin will attach those groups permission to this user.