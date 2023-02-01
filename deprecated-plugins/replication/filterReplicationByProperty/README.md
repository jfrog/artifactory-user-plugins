Artifactory Filter Replication by Property User Plugin
======================================================

This plugin filters items for replication by their properties; only items having
at least one of a specified set of properties will be replicated. Setting
properties in the `props` HashMultimap will ensure that artifacts with any of
those properties will be replicated, and all other artifacts will be skipped.
