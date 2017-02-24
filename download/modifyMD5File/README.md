Artifactory Modify MD5 File User Plugin
=======================================

*This plugin is currently only working in Artifactory 4.x. We are working on updating it to work in Artifactory 5.x.*

This plugin modifies attempts to download `*.md5` files so that a `*.md5.txt`
file is downloaded instead.

In this example, this only works in the `md5-test-remote` repository.
