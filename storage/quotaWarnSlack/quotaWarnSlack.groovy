import groovy.transform.Field
import org.artifactory.api.context.ContextHelper
import org.artifactory.api.security.UserGroupService
import org.artifactory.security.UserInfo
import org.artifactory.storage.StorageService
import net.gpedro.integrations.slack.SlackApi
import net.gpedro.integrations.slack.SlackAttachment
import net.gpedro.integrations.slack.SlackMessage


/**
 * Interval between storage checks (in ms)
 */
@Field
def executionInterval = 60000
@Field
NotificationsStrategy sendingNotificationsStrategy = new SendAlertNotificationsAtSomeRateStrategy(alertNotificationRate: 60)
//NotificationsStrategy sendingNotificationsStrategy = new LimitAlertNotificationsStrategy(maxNumberOfSuccessiveNotifications: 1)

jobs {
    scheduledQuotaCheck(delay: 100, interval: executionInterval) {
        quotaCheck()
    }
}

/**
 *
 * Checks every <code>executionInterval</code> if the storage space limit or storage space warning is reached.
 * This is configured in the UI at Admin > Advanced > Maintenance > Storage Quota.
 *
 * If the limit or warning is reached, a simple email with details is sent to all admins.
 *
 */
def quotaCheck() {
    StorageService storageService = ContextHelper.get().beanForType(StorageService)
    def storageQuotaInfo = storageService.getStorageQuotaInfo(0)
    if (storageQuotaInfo?.isLimitReached()) {
        trySendNotification("danger","[ERROR] ARTIFACTORY STORAGE QUOTA", storageQuotaInfo.getErrorMessage())
    } else if (storageQuotaInfo?.isWarningLimitReached()) {
        trySendNotification("warning","[WARN] ARTIFACTORY STORAGE QUOTA", storageQuotaInfo.getWarningMessage())
    } else {
        sendingNotificationsStrategy.reset()
    }
}

def notifySlack(String level,String headline,String message) {
    def slackApi = new SlackApi("WEBHOOK_URL");
    def slackMessage = new SlackMessage()
    slackMessage.setUsername("BOT")
    slackMessage.setIcon(":slack:")
    def hostname = InetAddress.getLocalHost().getHostName()
    slackMessage.setText(hostname + ": " + headline)
    def slackAttachment = new SlackAttachment()
    slackAttachment.setColor(level)
    slackAttachment.setText(message)
    slackAttachment.setFallback(hostname + ": " + headline)
    slackMessage.addAttachments(slackAttachment)
    slackApi.call(slackMessage)
}

def trySendNotification(String level, String headline, String message) {
    try {
        if (sendingNotificationsStrategy.shouldSendNotification()) {
            notifySlack(level,headline,message)
            log.warn("Quota notification sent to admin")
        }
    } catch (Exception e) {
        log.error("Error while sending storage quota message.", e)
    }
}

interface NotificationsStrategy {
    boolean shouldSendNotification()
    void reset()
}

/**
 * Send only N successive alert notices and then stop. It will resend notification once
 * the reset method will be called, for example when there's no more storage alert.
 */
class LimitAlertNotificationsStrategy implements NotificationsStrategy {

    private int currentNumberOfSuccessiveSentNotifications
    int maxNumberOfSuccessiveNotifications

    boolean shouldSendNotification() {
        boolean shouldSendNotification = false
        if (currentNumberOfSuccessiveSentNotifications < maxNumberOfSuccessiveNotifications) {
            shouldSendNotification = true
            currentNumberOfSuccessiveSentNotifications++
        }
        return shouldSendNotification
    }

    void reset() {
        currentNumberOfSuccessiveSentNotifications = 0
    }

}

/**
 * Send one notification every <code>alertNotificationRate</code> alerts.
 */
class SendAlertNotificationsAtSomeRateStrategy implements NotificationsStrategy {

    private int currentNumberOfAlerts

    int alertNotificationRate = 10

    boolean shouldSendNotification() {
        boolean shouldSendNotification = false
        if (currentNumberOfAlerts == 0) {
            shouldSendNotification = true
        } else if (currentNumberOfAlerts % alertNotificationRate == 0) {
            shouldSendNotification = true
        }
        currentNumberOfAlerts++
        return shouldSendNotification
    }
    void reset() {
        currentNumberOfAlerts = 0
    }

}
