Artifactory Build Promotion User Plugin
=======================================

A REST executable build promotion.

This plugin promotes a snapshot build to release. It does the following:

1. The build is copied with a "-r" suffix added to the build number to indicate
   release.
2. The produced artifacts and the dependencies are copied to the target release
   repository and renamed from snapshot to release version (using the repository
   layout or the snapExp parameter).
3. Descriptors (ivy.xml or pom.xml) are modified by replacing the versions from
   snapshot to release (including dependencies) and deployed to the target
   release repository.

Parameters
----------

- snapExp - Snapshot version regular expression. This is used as a fallback to
  determine how to transform a snapshot version string to a release one, in case
  repository layout information can't be used for this purpose (e.g. the layout
  doesn't match).
- targetRepository - The name of the repository to put the promoted build
  artifacts into.

For example:

`curl -X POST -uadmin:password http://localhost:8080/artifactory/api/plugins/build/promote/snapshotToRelease/gradle-multi-example/1?params=snapExp=d%7B14%7D|targetRepository=gradle-release-local`
