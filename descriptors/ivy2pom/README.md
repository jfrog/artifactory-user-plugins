Artifactory ivy2pom User Plugin
===============================

*This plugin is currently being tested for Artifactory 5.x releases.*

Whenever there is an attempt to download a nonexistant `pom.xml` file, this
plugin checks if there is an equivalent `ivy.xml` file. If the ivy file exists,
the plugin converts it to a pom file and deploys it.

Configuration
-------------

There are a few points in the source code that may require modification:
- The `TARGET_RELEASES_REPOSITORY` and `TARGET_SNAPSHOTS_REPOSITORY` variables
  contain the names of the release and snapshot repositories (respectively) to
  deploy the converted pom file to.
- The `IVY_DESCRIPTORS_REPOSITORY` variable contains the name of the
  repository to look for the ivy descriptor in.
- The string representing the ivy repository layout may need to be changed,
  depending on the layout your repository uses.
