# Not included in gradle build on purpose!

Because this Artifactory plugin needs Artifactory internals,
the actual plugin code cannot be build without running inside Artifactory.
Therefor, the actual plugin code is *not* included in the actual gradle build.

Artifactory is mostly "open source",
but not distributed as such.
Its "open source" part can be downloaded from:
https://api.bintray.com/content/jfrog/artifactory/jfrog-artifactory-oss-$latest-sources.tar.gz;bt_package=jfrog-artifactory-oss-zip

The "open source" Artifactory source is not 100%,
they remove parts of the code by replacing it with default or stub code before publishing.
That makes it hard to completely understand how Artifactory works.
But more importantly,
impossible to build and test Artifactory plugins completely,
as the Plugin API (papi) is really limited.
For anything useful,
you are dependent on the Artifactory internals,
sadly. 
