import groovy.transform.Field
import org.artifactory.api.context.ContextHelper
import org.artifactory.api.mail.MailService
import org.artifactory.api.security.UserGroupService
import org.artifactory.security.UserInfo
import org.artifactory.storage.StorageService
import org.artifactory.util.EmailException


/**
 * Interval between storage checks (in ms)
 */
@Field
def executionInterval = 10_000
@Field
//EmailNotificationsStrategy sendingMailStrategy = new SendAlertEmailsAtSomeRateStrategy(alertEmailRate: 10)
EmailNotificationsStrategy sendingMailStrategy = new LimitAlertEmailsStrategy(maxNumberOfSuccessiveEmails: 10)

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
    MailService mailService = ContextHelper.get().beanForType(MailService)
    def storageQuotaInfo = storageService.getStorageQuotaInfo(0)
    if (storageQuotaInfo?.isLimitReached()) {
        trySendMail(mailService, "[ERROR] ARTIFACTORY STORAGE QUOTA", storageQuotaInfo.getErrorMessage())
    } else if (storageQuotaInfo?.isWarningLimitReached()) {
        trySendMail(mailService, "[WARN] ARTIFACTORY STORAGE QUOTA", storageQuotaInfo.getWarningMessage())
    } else {
        sendingMailStrategy.reset()
    }
}

def trySendMail(def mailService, String subj, String message) {
    try {
        if (sendingMailStrategy.shouldSendMail()) {
            def adminEmails = findAdminEmails() as String[]
            mailService.sendMail(adminEmails, subj, message)
            log.warn("Quota mail notification sent to admin")
        }
    } catch (EmailException e) {
        log.error("Error while sending storage quota mail message.", e)
    }
}

List findAdminEmails() {
    UserGroupService userGroupService = ContextHelper.get().beanForType(UserGroupService)
    userGroupService.getAllUsers(true).findAll { UserInfo userInfo -> userInfo.isAdmin() && userInfo.email }.collect { it.email }
}

interface EmailNotificationsStrategy {
    boolean shouldSendMail()
    void reset()
}

/**
 * Send only N successive alert emails and then stop. It will resend email once
 * the reset method will be called, for example when there's no more storage alert.
 */
class LimitAlertEmailsStrategy implements EmailNotificationsStrategy {

    private int currentNumberOfSuccessiveSentEmails
    int maxNumberOfSuccessiveEmails

    boolean shouldSendMail() {
        boolean shouldSendMail = false
        if (currentNumberOfSuccessiveSentEmails < maxNumberOfSuccessiveEmails) {
            shouldSendMail = true
            currentNumberOfSuccessiveSentEmails++
        }
        return shouldSendMail
    }

    void reset() {
        currentNumberOfSuccessiveSentEmails = 0
    }

}

/**
 * Send one email every <code>alertEmailRate</code> alerts.
 */
class SendAlertEmailsAtSomeRateStrategy implements EmailNotificationsStrategy {

    private int currentNumberOfAlerts

    int alertEmailRate = 10

    boolean shouldSendMail() {
        boolean shouldSendMail = false
        if (currentNumberOfAlerts == 0) {
            shouldSendMail = true
        } else if (currentNumberOfAlerts % alertEmailRate == 0) {
            shouldSendMail = true
        }
        currentNumberOfAlerts++
        return shouldSendMail
    }
    void reset() {
        currentNumberOfAlerts = 0
    }


}
