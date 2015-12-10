import com.icegreen.greenmail.util.GreenMail
import com.icegreen.greenmail.util.ServerSetup

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

    def 'test log sending quota warn emails'() {
        setup:
        Artifactory artifactory = create("http://localhost:8088/artifactory", "admin", "password")
        ServerSetup setup = new ServerSetup(3025, "localhost", "smtp");
        GreenMail greenMail = new GreenMail(setup)
        greenMail.start()
        // TODO : configure artifactory with the right quota and smtp server localhost:3025 and an admin email of admin@test.local

        when:
        greenMail.waitForIncomingEmail(15000, 1)

        then:
        def messages = greenMail.getReceivedMessages()
        MimeMessage message = messages.find { MimeMessage message -> message.subject.contains('[WARN] ARTIFACTORY STORAGE QUOTA') }
        assert message
        assert message.allRecipients.each { InternetAddress addr -> assert addr.address == 'admin@test.local' }

        cleanup:
        greenMail.stop()
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
