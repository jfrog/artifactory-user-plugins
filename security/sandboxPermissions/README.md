Artifactory Protect Properties Edit User Plugin
=======================================

This plugin is for use in 'sandbox' repositories, and allows a user to claim any unused
namespace (defined as a top-level directory in repository root) and manage it via 
properties.  This plugin assumes that the user already has write permissions on the 
repository in question, and allows any user with the annotate permission to modify 
permissions on objects.

Permissions are managed by the property: sandboxPerms.ownerUsers  this is a COMMA SEPARATED LIST
of users, and can be modified by anyone with the annotate permission.  If the property does not
exist, the file may not be deleted and the next deploy to the namespace will set the property.

You may also declare allowed groups with the property sandboxPerms.ownerGroups - there still
must be an owning user but groups will otherwise have all permissions

Installation
---------------------

This plugin needs to be added to the `$ARTIFACTORY_HOME/etc/plugins` directory.