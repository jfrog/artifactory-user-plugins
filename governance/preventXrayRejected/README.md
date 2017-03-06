Artifactory Prevent Xray Rejected User Plugin
=============================================

*This plugin is currently being tested for Artifactory 5.x releases.*

This plugin rejects downloads of artifacts which have been marked with an alert
by Xray. By default, all artifacts with alerts are rejected, but you may modify
the `rejectSeverities` variable if you wish to only reject artifacts with alerts
of certain severities.
