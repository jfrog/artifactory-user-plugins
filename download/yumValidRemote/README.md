Artifactory Yum Valid Remote User Plugin
========================================

*This plugin is currently only working in Artifactory 4.x. We are working on updating it to work in Artifactory 5.x.*

Older versions of yum will update the repodata files without changing their
filenames. This can lead to caching issues, which can cause version and checksum
mismatch. This plugin attempts to alleviate this issue, by transforming repodata
files on the fly when requested from a remote repository.

The first time this plugin is used, or after the cache is cleared, metadata
files in the process of downloading may 404. Once this initial download is
complete, the correct metadata files should always be available, even when there
are updates on the remote server. This is because the plugin refuses to index
new metadata until after all the required metadata files are cached, and it will
continue to use the old metadata in the meantime.
