Artifactory PGP Sign User Plugin
================================

Artifactory user plugin that signs all incoming artifacts using the key and
passphrase specified in `$ARTIFACTORY_HOME/etc/plugins/pgpSign.properties`, and
deploys the resulting signature in typical fashion as an .asc file parallel to
the original artifact.

The plugin will be activated only on local repositories that are defined in `pgpSign.properties`.
Please note that it is important to list the repositories as a comma-separated list, without parentheses nor quotation marks (",[,]).

Purpose-built to meet the needs of promoting artifacts to Maven Central, i.e.
not intended in its current form for more general use.
