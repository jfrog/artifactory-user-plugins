Artifactory Yum Valid Remote User Plugin
========================================

Older versions of yum will update the repodata files without changing their
filenames. This can lead to caching issues, which can cause version and checksum
mismatch. This plugin attempts to alleviate this issue.

Whenever a repomd.xml file is requested from a remote repository, this plugin
compares the checksums of all the repodata files against those listed in the
repomd file. If there are any mismatches, this plugin deletes the mismatched
files from the cache, so that they can be updated to the correct version on the
next request.
