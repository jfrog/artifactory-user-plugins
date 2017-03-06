Artifactory Internal Rewrite Download User Plugin
=================================================

*This plugin is currently being tested for Artifactory 5.x releases.*

This plugin creates a virtual symlink called "latest", which redirects to the
directory provided from the value of the `latest.folderName` property set on the
root folder of the repository.

In this example, this only works in the `dist-local` repository.
