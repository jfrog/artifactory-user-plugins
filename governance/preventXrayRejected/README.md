Artifactory Prevent Xray Rejected User Plugin
=============================================

*This plugin is currently only working in Artifactory 4.x. We are working on updating it to work in Artifactory 5.x.*

This plugin rejects downloads of artifacts which have been marked with an alert
by Xray. By default, all artifacts with alerts are rejected, but you may modify
the `rejectSeverities` variable if you wish to only reject artifacts with alerts
of certain severities.
