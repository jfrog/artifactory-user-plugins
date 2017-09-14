Artifactory Storage Quota Warn Slack User Plugin
=============================================

This plugin is used to send notification messages to a Slack channel when the storage quota warning / limit is reached.
Storage quotas are configured in the Artifactory UI at Admin > Advanced > Maintenance > Storage Quota.

Features
--------

- Sends notification messages to a Slack channel when the storage quota warning or the storage quota limit are reached.

Message text is :
- `hostname: [WARN] ARTIFACTORY STORAGE QUOTA` in case of a storage space warning or
- `hostname: [ERROR] ARTIFACTORY STORAGE QUOTA` in case of reaching the storage limit.

The message attachment text is the exact same message shown in the UI, for example :

`Datastore disk space is too high: Max limit: 30%, Used: 32%, Total: 464.78 GB, Used: 149.21 GB, Available: 315.58 GB`

Installation
------------

To install this plugin:

1. Place the configuration file `quotaWarnSlack.json` under the master Artifactory server `${ARTIFACTORY_HOME}/etc/plugins` folder
2. Edit the configuration file, replacing `WEBHOOK_URL` with your Slack Incoming WebHook URL from the Setup instructions in Slack
3. Place the plugin script file `quotaWarnSlack.groovy` under the master Artifactory server `${ARTIFACTORY_HOME}/etc/plugins` folder
4. Verify in Artifactory's log that the plugin loaded correctly.

Usage
-----

This plugin is a scheduled task, fired every 60s. If needed, you can change this at the begining of the quotaWarnSlack.groovy : `executionInterval = 60000`

### Limit the number of messages ###
At the begining of que plugin script you can also select one of the two provided strategies for limiting the number of notification messages :

- Send one message every 60 alerts until the alert is over (Default)
```JAVA
NotificationsStrategy sendingNotificationsStrategy = new SendAlertNotificationsAtSomeRateStrategy(alertNotificationRate: 60)
```
-  Send only 1 sucessive message and then stop
```JAVA
NotificationsStrategy sendingNotificationsStrategy = new LimitAlertNotificationsStrategy(maxNumberOfSuccessiveNotifications: 1)
```

