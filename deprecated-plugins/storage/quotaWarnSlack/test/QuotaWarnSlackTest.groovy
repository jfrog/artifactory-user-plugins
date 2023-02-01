import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import groovy.xml.XmlUtil
import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import spock.lang.Shared
import spock.lang.Specification

import org.jfrog.artifactory.client.ArtifactoryClientBuilder

class QuotaWarnSlackTest extends Specification {

    static final def baseurl = 'http://localhost:8088/artifactory'
    static final auth = "Basic ${("admin:password").bytes.encodeBase64().toString()}"
    @Shared
    def artifactory = ArtifactoryClientBuilder.create().setUrl(baseurl)
        .setUsername('admin').setPassword('password').build()

    static final def slackMockServerPort = 8000

    def 'receive valid slack quota warning notification test'() {
        setup:
        def expectedMessageContent = '[WARN] ARTIFACTORY STORAGE QUOTA'
        // Start slack mock server
        def slackMockServer = new SlackMockServer(port: slackMockServerPort, requiredMessage: expectedMessageContent)
        slackMockServer.start()
        // Reset notification strategy
        resetNotificationStragety()

        // Add quota configuration
        addQuotaConfiguration(0, 99)

        when:
        // Wait for notification receiving
        slackMockServer.waitForMessage()

        then:
        // A message must have been received
        slackMockServer.messageReceived == true
        // And it must be a valid one
        slackMockServer.validMessageReceived == true

        cleanup:
        // Remove quota configuration
        deleteQuotaConfiguration()
        // Stop slack mock server
        slackMockServer?.stop()
    }

    def 'receive valid slack quota error notification test'() {
        setup:
        def expectedMessageContent = '[ERROR] ARTIFACTORY STORAGE QUOTA'
        // Start slack mock server
        def slackMockServer = new SlackMockServer(port: slackMockServerPort, requiredMessage: expectedMessageContent)
        slackMockServer.start()
        // Reset notification strategy
        resetNotificationStragety()

        // Add quota configuration
        addQuotaConfiguration(0, 0)

        when:
        // Wait for notification receiving
        slackMockServer.waitForMessage()

        then:
        // A message must have been received
        slackMockServer.messageReceived == true
        // And it must be a valid one
        slackMockServer.validMessageReceived == true

        cleanup:
        // Remove quota configuration
        deleteQuotaConfiguration()
        // Stop slack mock server
        slackMockServer?.stop()
    }

    /**
     * Set slack webhook notification url
     * @param url
     */
    def setSlackWebhookUrl(url) {
        try {
            println "Setting slack webhook url to $url..."
            def conn = new URL(baseurl + '/api/plugins/execute/setSlackWebhookUrl').openConnection()
            conn.setRequestProperty('Authorization', auth)
            conn.setDoOutput(true)
            conn.setRequestMethod('POST')
            conn.setRequestProperty('Content-Type', 'application/json')

            def jsonPayload = new JsonBuilder()
            jsonPayload {
                webhookUrl url
            }

            conn.outputStream.withCloseable { output ->
                output.write(jsonPayload.toString().bytes)
            }
            println "Response code ${conn.responseCode}"
            assert conn.responseCode == 200
        } catch (Exception e) {
            e.printStackTrace()
            throw e
        }
    }

    /**
     * Reset notification strategy so it will send the notification as soon as the first event is received
     * @return
     */
    def resetNotificationStragety() {
        artifactory.plugins().execute('resetSlackNotificationStrategy').sync()
    }

    /**
     * Add Quota configuration
     * @return
     */
    def addQuotaConfiguration(warningPercentage, limitPercentage) {
        println 'Setting up Quota Configuration...'
        def config = artifactory.system().configuration()
        def newConfig = new XmlSlurper(false, false).parseText(config)

        def quotaConfig = {
            quotaConfig {
                enabled true
                diskSpaceLimitPercentage limitPercentage
                diskSpaceWarningPercentage warningPercentage
            }
        }

        if (newConfig.quotaConfig.size() == 1) {
            newConfig.quotaConfig.replaceNode quotaConfig
        } else {
            // Add quotaConfig node after virtualCacheCleanupConfig node
            newConfig.virtualCacheCleanupConfig + quotaConfig
        }

        artifactory.system().configuration(XmlUtil.serialize(newConfig))
    }

    /**
     * Remove Quota configuration
     * @return
     */
    def deleteQuotaConfiguration() {
        println 'Removing Quota Configuration...'
        def config = artifactory.system().configuration()
        def newConfig = new XmlSlurper(false, false).parseText(config)

        newConfig.quotaConfig.enabled = false

        artifactory.system().configuration(XmlUtil.serialize(newConfig))
    }

    /**
     * Slack mock server
     *
     * This server is responsible for receiving and validating slack notifications requests
     */
    class SlackMockServer {

        HttpServer server
        int port = 8000
        boolean messageReceived
        boolean validMessageReceived
        String requiredMessage

        def start() {
            InetSocketAddress address = new InetSocketAddress(port);
            server = HttpServer.create(address, 0);

            HttpHandler slackRequestValidatorHandler = new HttpHandler() {
                @Override
                void handle(HttpExchange exchange) throws IOException {

                    println "Slack mock server receive a new request"
                    messageReceived = true
                    def content = exchange.requestBody.text
                    println "Message content: $content"

                    validMessageReceived = isMessageContentValid(content)

                    exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, 0)
                    exchange.close()
                }

                /**
                 * Check if message content is valid
                 * @param content
                 * @return
                 */
                boolean isMessageContentValid(content) {
                    def jsonContent = new JsonSlurper().parseText(content)

                    assert jsonContent.username != null
                    assert jsonContent.icon_emoji != null
                    assert jsonContent.text != null
                    assert jsonContent.attachments[0].color != null
                    assert jsonContent.attachments[0].text != null
                    assert jsonContent.attachments[0].fallback != null
                    if (requiredMessage != null) {
                        assert jsonContent.text.contains(requiredMessage)
                    }

                    return true
                }
            }

            server.createContext("/", slackRequestValidatorHandler)
            server.start()
            println "Slack mock server started"
        }

        def stop() {
            server?.stop(0)
            println "Slack mock server stopped"
        }

        def waitForMessage() {
            println "Waiting up to 2 minutes for notification receiving..."
            def initialTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - initialTime < 120000) {
                sleep(5000)
                if (this.messageReceived) {
                    // Wait a little bit more for message to be consumed
                    sleep (5000)
                    break
                }
            }
        }
    }
}
