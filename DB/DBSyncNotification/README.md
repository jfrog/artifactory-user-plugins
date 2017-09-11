Artifactory DB Sync Notification User Plugin
============================================

This plugin allows you to force a database sync in an HA cluster.

For efficiency reasons, Artifactory caches some database resources locally
(configuration files, permissions information, etc). In an HA cluster, each node
has its own cache, so to stay up to date with each other, the nodes send
database sync messages whenever a cached resource changes. This plugin allows
you to send these messages manually, whenever you'd like.

Usage
-----

This plugin exposes a REST endpoint that, when called, sends database sync
messages to all nodes in the HA cluster. For example:

``` shell
curl -v -uadmin:password -XPOST http://localhost:8081/artifactory/api/plugins/execute/syncNotification
```
