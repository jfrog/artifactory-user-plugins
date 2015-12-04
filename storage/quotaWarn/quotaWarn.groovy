import groovy.transform.Field
import org.artifactory.api.context.ContextHelper
import org.artifactory.api.mail.MailService
import org.artifactory.storage.StorageService
import org.artifactory.util.EmailException


@Field
def adminEmailAddress = 'admin@test.local'
@Field
def executionInterval = 10000

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
 * If the limit or warning is reached, a simple email with details is sent to <code>adminEmailAddress</code>
 *
 */
def quotaCheck() {
    StorageService storageService = ContextHelper.get().beanForType(StorageService)
    MailService mailService = ContextHelper.get().beanForType(MailService)
    def storageQuotaInfo = storageService.getStorageQuotaInfo(0)
    if (storageQuotaInfo.isLimitReached()) {
        trySendMail(mailService, "[ERROR] ARTIFACTORY STORAGE QUOTA", storageQuotaInfo.getErrorMessage())
    } else if (storageQuotaInfo.isWarningLimitReached()) {
        trySendMail(mailService, "[WARN] ARTIFACTORY STORAGE QUOTA", storageQuotaInfo.getWarningMessage())
    }
}

def trySendMail(def mailService, String subj, String message) {
    try {
        mailService.sendMail([adminEmailAddress] as String[], subj, message)
        log.warn("Quota mail notification sent to admin")
    } catch (EmailException e) {
        log.error("Error while sending storage quota mail message.", e)
    }
}