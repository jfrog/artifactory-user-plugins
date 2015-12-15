Artifactory Storage Quota Warning User Plugin
=============================================

This plugin is used to send email notifications when the storage quota warning / limit is reached.
This is configured in the Artifactory UI at Admin > Advanced > Maintenance > Storage Quota.

Installation
------------

To install this plugin:

1. Place this script under the master Artifactory server
   `${ARTIFACTORY_HOME}/etc/plugins`.
2. Verify in the ${ARTIFACTORY_HOME}/logs/artifactory.log that the plugin loaded
   correctly.

Features
--------

- Send email notification to all admin users when the storage quota warning or the storage quota limit are reached.

The email subject is :
- `[Artifactory] [WARN] ARTIFACTORY STORAGE QUOTA` in case of a storage space warning or
- `[Artifactory] [ERROR] ARTIFACTORY STORAGE QUOTA` in case of reaching the storage limit.

The email content is the exact same message shown in the UI, for example :

`Datastore disk space is too high: Max limit: 30%, Used: 32%, Total: 464.78 GB, Used: 149.21 GB, Available: 315.58 GB`

### Execution ###
This plugin is a scheduled task, fired every 10s by default.
You can change this at the begining of the quotaWarn.groovy :
`executionInterval = 10_000`

### Limit the number of sent emails ###
You can use one of the two provided strategies for limiting the number of sent notifications :

-  Send only 10 sucessive emails (default) and then stop
```JAVA
EmailNotificationsStrategy sendingMailStrategy ` = new LimitAlertEmailsStrategy(maxNumberOfSuccessiveEmails: 10)
```
- Send one email every 10 alerts until the alert is over
```JAVA
EmailNotificationsStrategy sendingMailStrategy = new SendAlertEmailsAtSomeRateStrategy(alertEmailRate: 10)
```




