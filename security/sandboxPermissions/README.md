Artifactory Protect Properties Edit User Plugin
=======================================

This plugin is for use in 'sandbox' repositories, and allows a user to claim any unused
namespace (defined as a top-level directory in repository root) and manage it via 
properties.

This plugin assumes that the user already has write permissions on the
repository in question, annotate permissions are required to change permissions, and any user
who has the ability to modify a namespace will be able to modify its permissions.

A result of this plugin is that no user will be allowed to write to the root directory of
a repository.  It requires that all data pushed to the repository have a namespace.  Note
that a docker image, because it is represented as a hierarchy of directories already, can be
pushed without a namespace beyond the imagename itself.

Only repositories in the list checkedRepositories declared at top are checked.

Permissions are managed by the property: sandboxPerms.ownerUsers  this is a SLASH DELIMITED LIST
of users (e.g. markg/stanleyf/dev1), and can be modified by anyone with the annotate permission.  If the property does not
exist, the file may not be deleted and the next deploy to the namespace will set the property.

You may also declare allowed groups with the property sandboxPerms.ownerGroups - there still
must be an owning user but groups will otherwise have all permissions

Installation
---------------------

This plugin needs to be added to the `$ARTIFACTORY_HOME/etc/plugins` directory.