Artifactory Storage Quota Warn Slack User Plugin
=============================================

This plugin is used to send notification messages to a Slack channel when the storage quota warning / limit is reached.
Storage quotas are configured in the Artifactory UI at Admin > Advanced > Maintenance > Storage Quota.

Installation
------------

To install this plugin:

1. Download the latest release of the Slack Webhook Integration for Java project
   at https://github.com/gpedro/slack-webhook
   or alternatively clone the repository and build from source
2. Place the slack-webhook jar file under the master Artifactory server
   `${ARTIFACTORY_HOME}/etc/plugins/lib`.
3. Edit this script, replacing `WEBHOOK_URL` with your Slack Incoming WebHook URL from the Setup instructions in Slack
4. Place this script under the master Artifactory server
   `${ARTIFACTORY_HOME}/etc/plugins`.
5. Verify in the ${ARTIFACTORY_HOME}/logs/artifactory.log that the plugin loaded
   correctly.

Features
--------

- Sends notification messages to a Slack channel when the storage quota warning or the storage quota limit are reached.

Message text is :
- `hostname: [WARN] ARTIFACTORY STORAGE QUOTA` in case of a storage space warning or
- `hostname: [ERROR] ARTIFACTORY STORAGE QUOTA` in case of reaching the storage limit.

The message attachment text is the exact same message shown in the UI, for example :

`Datastore disk space is too high: Max limit: 30%, Used: 32%, Total: 464.78 GB, Used: 149.21 GB, Available: 315.58 GB`

### Execution ###
This plugin is a scheduled task, fired every 60s by default.
You can change this at the begining of the quotaWarnSlack.groovy :
`executionInterval = 60000`

### Limit the number of messages ###
You can use one of the two provided strategies for limiting the number of notification messages :

- Send one message every 60 alerts (default) until the alert is over
```JAVA
NotificationsStrategy sendingNotificationsStrategy = new SendAlertNotificationsAtSomeRateStrategy(alertNotificationRate: 60)
```
-  Send only 1 sucessive message and then stop
```JAVA
NotificationsStrategy sendingNotificationsStrategy = new LimitAlertNotificationsStrategy(maxNumberOfSuccessiveNotifications: 1)
```

