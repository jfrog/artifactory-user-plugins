Artifactory beforeDownloadRequest Sample User Plugin
====================================================

*This plugin is currently only working in Artifactory 4.x. We are working on updating it to work in Artifactory 5.x.*

A sample implementation of the beforeDownloadRequest execution point. This
plugin causes JSON files to expire and be recached when downloaded from a remote
repository, if the file has been in the cache for over an hour.
