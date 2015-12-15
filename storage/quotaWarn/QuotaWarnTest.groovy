import com.icegreen.greenmail.util.GreenMail
import com.icegreen.greenmail.util.ServerSetup
import org.jfrog.artifactory.client.ArtifactoryRequest
import org.jfrog.artifactory.client.impl.ArtifactoryRequestImpl
import org.jfrog.artifactory.client.model.User
import org.jfrog.artifactory.client.model.builder.UserBuilder

import javax.mail.Address
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

import static org.jfrog.artifactory.client.ArtifactoryClient.create

import groovy.json.JsonSlurper
import org.jfrog.artifactory.client.Artifactory

import spock.lang.Specification

/**
 * Testing sending email when storage quota is reached.
 * This test needs GreenMail in the classpath.
 */
class QuotaWarnTest extends Specification {

    Artifactory artifactory
    String smtpHost = "localhost"
    Integer smtpPort = 3025
    String artUrl = "http://localhost:8088/artifactory"

    def 'test log sending quota warn emails'() {
        setup:
        artifactory = create(artUrl, "admin", "password")
        ServerSetup setup = new ServerSetup(smtpPort, smtpHost, "smtp");
        GreenMail greenMail = new GreenMail(setup)
        greenMail.start()

        Integer pctUsedInt = getPctUsed()
        String savedConfig = artifactory.system().configuration()
        updateConfigurationWithMailAndQuota(savedConfig, pctUsedInt)
        updateAdminEmail()

        when:
        greenMail.waitForIncomingEmail(15000, 1)

        then:
        def messages = greenMail.getReceivedMessages()
        MimeMessage message = messages.find { MimeMessage message ->
            message.subject.contains('[WARN] ARTIFACTORY STORAGE QUOTA')
        }
        assert message
        assert message.allRecipients.each { InternetAddress addr -> assert addr.address == 'admin@test.local' }

        cleanup:
        if (savedConfig) {
            artifactory.system().configuration(savedConfig)
        }
        greenMail?.stop()
    }

    private void updateAdminEmail() {
        UserBuilder userBuilder = artifactory.security().builders().userBuilder();
        User user = userBuilder.name('admin').password('password').email("admin@test.local").admin(true).build();
        artifactory.security().createOrUpdate(user);
    }

    private void updateConfigurationWithMailAndQuota(String savedConfig, int pctUsedInt) {
        String conf = savedConfig.replace('</addons>',
                """
            </addons>
            <mailServer>
                <enabled>true</enabled>
                <host>${smtpHost}</host>
                <port>${smtpPort}</port>
                <subjectPrefix>[Artifactory]</subjectPrefix>
                <tls>false</tls>
                <ssl>false</ssl>
                <artifactoryUrl>${artUrl}</artifactoryUrl>
            </mailServer>
            """).replace('</virtualCacheCleanupConfig>',
                """
            </virtualCacheCleanupConfig>
            <quotaConfig>
                <enabled>true</enabled>
                <diskSpaceLimitPercentage>90</diskSpaceLimitPercentage>
                <diskSpaceWarningPercentage>${pctUsedInt}</diskSpaceWarningPercentage>
            </quotaConfig>
            """)
        artifactory.system().configuration(conf)
    }

    private Integer getPctUsed() {
        // TODO : when artifactory-java-client-services 1.2 is released, use artifactory.storage() instead of this call
        ArtifactoryRequest storageRequest = new ArtifactoryRequestImpl()
                .apiUrl("api/storageinfo")
                .responseType(ArtifactoryRequest.ContentType.JSON)
                .method(ArtifactoryRequest.Method.GET)
        Map<String, Object> result = artifactory.restCall(storageRequest)
        String pctUsedFullStr = result.fileStoreSummary.usedSpace
        def pctUsed = pctUsedFullStr[pctUsedFullStr.indexOf('(') + 1..pctUsedFullStr.indexOf(',') - 1]
        def pctUsedInt = (pctUsed as Integer) - 1
        return pctUsedInt
    }

    def 'test sending mail limit strategy'() {
        setup:
        def sendingMailStrategy = new LimitAlertEmailsStrategy(maxNumberOfSuccessiveEmails: 10)

        when: 'sending 10 mails'
        10.times { assert sendingMailStrategy.shouldSendMail() }

        then: 'the next time that shouldn \'t send mail'
        10.times { assert !sendingMailStrategy.shouldSendMail() }
    }

    def 'test send mail limit strategy reset'() {
        setup:
        def sendingMailStrategy = new LimitAlertEmailsStrategy(maxNumberOfSuccessiveEmails: 10)

        when: 'sending 10 mails'
        10.times { assert sendingMailStrategy.shouldSendMail() }

        then: 'next time we shouldn\'t send mail'
        assert !sendingMailStrategy.shouldSendMail()

        when: 'resetting'
        sendingMailStrategy.reset()

        then: 'it should send again the next 10 mails'
        10.times { assert sendingMailStrategy.shouldSendMail() }

        then: 'it shouldn\'t send the next emails'
        assert !sendingMailStrategy.shouldSendMail()
        assert !sendingMailStrategy.shouldSendMail()
    }

    def 'test send mail rate strategy'() {
        setup:
        def sendingMailStrategy = new SendAlertEmailsAtSomeRateStrategy(alertEmailRate: 10)

        when: 'sending 1 mail'
        assert sendingMailStrategy.shouldSendMail()

        then: 'next 9 shouldn\'t send mails'
        9.times { assert !sendingMailStrategy.shouldSendMail() }

        then: 'next 1 should send mail'
        assert sendingMailStrategy.shouldSendMail()
    }

    def 'test send mail rate strategy reset'() {
        setup:
        def sendingMailStrategy = new SendAlertEmailsAtSomeRateStrategy(alertEmailRate: 10)

        when: 'sending 1 mail'
        sendingMailStrategy.shouldSendMail()

        then: 'next 5 shouldn\'t send mails'
        5.times { assert !sendingMailStrategy.shouldSendMail() }

        then: 'reset'
        sendingMailStrategy.reset()

        then: 'next 1 should send mail'
        assert sendingMailStrategy.shouldSendMail()
    }

}
