Artifactory Create-Copy User Plugin
========================================

This plugin mimics push replication between repos in a single Artifactory
instance. Source and destination repositories are defined in
createCopy.properties. The destination repository needs to be created before
copying takes place (the plugin won't create repositories).

"repository" is a list of source repos, and "repocopy" contains corresponding
destinations. The first "repository" in the list is copied to the first item in
"repocopy," and so on down the list. e.g.,

repository = ["libs-release-local","test-local"]
repocopy   = ["libs-release-copy","test-copy"]

In this case, artifacts created in libs-release-local will be copied to
libs-release-copy, and those created in test-local will be copied to test-copy.
