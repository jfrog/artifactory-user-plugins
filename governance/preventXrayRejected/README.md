Artifactory Prevent Xray Rejected User Plugin
=============================================
**This plugin is deprecated and will not be tested against new versions. Xray (since 1.12) no longer uses properties to denote severity. The same behavior can be accomplished with Xray Policies.**

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
