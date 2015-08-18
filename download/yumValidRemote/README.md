Artifactory Yum Valid Remote User Plugin
========================================

Older versions of yum will update the repodata files without changing their
filenames. This can lead to caching issues, which can cause version and checksum
mismatch. This plugin attempts to alleviate this issue, by transforming repodata
files on the fly when requested from a remote repository.

Due to a limitation of the Artifactory User Plugin system, it may take a while
to initially cache some repodata files. This can cause yum to timeout one or
more times before successfully downloading these files. This should only be a
problem when Artifactory needs to recache these files. If yum timing out causes
grief, change the `timeout` option in the `/etc/yum.conf` file.
