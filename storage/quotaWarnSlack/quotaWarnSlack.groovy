/*
 * Copyright (C) 2017 JFrog Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import groovy.transform.Field
import org.artifactory.api.context.ContextHelper
import org.artifactory.storage.StorageService
import org.artifactory.resource.ResourceStreamHandle
import groovy.json.JsonBuilder
import groovy.json.JsonSlurper

/**
 * Interval between storage checks (in ms)
 */
@Field
def executionInterval = 60000
@Field
NotificationsStrategy sendingNotificationsStrategy = new SendAlertNotificationsAtSomeRateStrategy(alertNotificationRate: 60)
//NotificationsStrategy sendingNotificationsStrategy = new LimitAlertNotificationsStrategy(maxNumberOfSuccessiveNotifications: 1)

/**
 * Configuration file path
 */
@Field
def configurationFilePath = 'plugins/quotaWarnSlack.json'

jobs {
    scheduledQuotaCheck(delay: 100, interval: executionInterval) {
        quotaCheck()
    }
}

executions {
    /**
     * Set slack webhook url.
     *
     * Expected json payload:
     * {
     *     "webhookUrl": "WEBHOOK_URL"
     * }
     *
     */
    setSlackWebhookUrl() { ResourceStreamHandle body ->
        bodyJson = new JsonSlurper().parse(body.inputStream)
        setWebhookUrl(bodyJson.webhookUrl)
        message = '{"status":"okay", "message":"webhook url set"}'
        status = 200
    }

    /**
     * Reset notification strategy
     */
    resetSlackNotificationStrategy() {
        sendingNotificationsStrategy.reset()
        log.info "Slack quota notification strategy reset executed"
        message = '{"status":"okay", "message":"notification strategy reset executed"}'
        status = 200
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

/**
 * Send slack notification
 * @param level
 * @param headline
 * @param message
 * @return
 */
def notifySlack(String level,String headline,String message) {
    def webhookUrl = getWebhookUrl()
    log.info "Quota notification message will be send to $webhookUrl"
    // Setup slack message payload
    def hostname = InetAddress.getLocalHost().getHostName()
    JsonBuilder builder = new JsonBuilder()
    builder {
        username 'BOT'
        icon_emoji ':slack:'
        text hostname + ': ' + headline
        attachments ([
            {
                color level
                text message
                fallback hostname + ": " + headline
            }

        ])
    }

    // Open connection and do output
    def conn = new URL(webhookUrl).openConnection()
    conn.setRequestMethod('POST')
    conn.setDoOutput(true)
    conn.setRequestProperty('Content-Type', 'application/json')
    conn.outputStream.withCloseable { output ->
        output.write(builder.toString().bytes)
    }

    // Handle response
    def responseCode = conn.responseCode
    if (responseCode != 200) {
        log.error "Failed to send slack quota notification. Response code: $responseCode"
        conn.inputStream.withCloseable { input ->
            log.error "Received message: ${input.text}"
        }
    }
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

/**
 * Load Slack Webhook url from configuration file
 */
def getWebhookUrl() {
    def etcdir = ctx.artifactoryHome.etcDir
    def configFile = new File(etcdir, configurationFilePath)
    def props = new JsonSlurper().parse(configFile)
    def url = props.webhookUrl
    return url
}

/**
 *  Seet Webhook url at configuration file
 */
def setWebhookUrl(url) {
    def etcdir = ctx.artifactoryHome.etcDir
    def configFile = new File(etcdir, configurationFilePath)
    def json = new JsonBuilder()
    json {
        webhookUrl url
    }
    configFile.write(json.toPrettyString())
    log.info "Slack webhook url set: ${url}"
    return url
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
