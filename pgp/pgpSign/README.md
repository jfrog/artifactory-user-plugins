Artifactory PGP Sign User Plugin
================================

Artifactory user plugin that signs all incoming artifacts using the key and
passphrase specified in `$ARTIFACTORY_HOME/etc/plugins/pgpSign.properties`, and
deploys the resulting signature in typical fashion as an .asc file parallel to
the original artifact.

The plugin will be activated only on repository keys ending with `-local`.

Purpose-built to meet the needs of promoting artifacts to Maven Central, i.e.
not intended in its current form for more general use.
