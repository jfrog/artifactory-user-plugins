Artifactory Prevent Xray Rejected User Plugin
=============================================

This plugin rejects downloads of artifacts which have been marked with an alert
by Xray. By default, all artifacts with alerts are rejected, but you may modify
the `rejectSeverities` variable if you wish to only reject artifacts with alerts
of certain severities.

Features
--------
Change `rejectSeverities` to block download of all artifacts with severities `Minor` and `Critical`.

e.g.
```
rejectSeverities = ['Minor', 'Critical']
```

Installation
------------

Place plugin under ${ARTIFACTORY_HOME}/etc/plugins/
