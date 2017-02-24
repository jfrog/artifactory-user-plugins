Artifactory Filter Replication by Property User Plugin
======================================================

*This plugin is currently only working in Artifactory 4.x. We are working on updating it to work in Artifactory 5.x.*

This plugin filters items for replication by their properties; only items having
at least one of a specified set of properties will be replicated. Setting
properties in the `props` HashMultimap will ensure that artifacts with any of
those properties will be replicated, and all other artifacts will be skipped.