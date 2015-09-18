Layout Properties Plugin
========================

To Install:
-----------

Copy this plugin into your `$ARTIFACTORY_HOME/etc/plugins` directory.

Function:
---------

This plug-in functions whenever an aritifact is deployed. It takes all the
tokens from your layout (such as `baseRev`, `fileItegRev`, `module`, `orgPath`
etc) and creates properties prefixed with a fixed prefix (by default `layout.`).
It also creates properties for any custom tokens you might create.
