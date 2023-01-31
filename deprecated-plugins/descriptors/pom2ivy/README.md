Artifactory pom2ivy User Plugin
===============================

Whenever there is an attempt to download a nonexistant `ivy.xml` file, this
plugin checks if there is an equivalent `pom.xml` file. If the pom file exists,
the plugin converts it to an ivy file and deploys it.

Configuration
-------------

There are a few points in the source code that may require modification:
- The `TARGET_RELEASES_REPOSITORY` and `TARGET_SNAPSHOTS_REPOSITORY` variables
  contain the names of the release and snapshot repositories (respectively) to
  deploy the converted ivy file to.
- The `MAVEN_DESCRIPTORS_REPOSITORY` variable contains the name of the
  repository to look for the maven descriptor in.
