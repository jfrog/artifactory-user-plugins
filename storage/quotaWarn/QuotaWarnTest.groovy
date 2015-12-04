import com.icegreen.greenmail.util.GreenMail
import com.icegreen.greenmail.util.ServerSetup

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
        // TODO : configure artifactory with the right quota and smtp server localhost:3025

        when:
        greenMail.waitForIncomingEmail(15000, 1)

        then:
        def messages = greenMail.getReceivedMessages()
        println "messages : ${messages[0].subject}"
        MimeMessage message = messages.find { MimeMessage message -> message.subject.contains('[WARN] ARTIFACTORY STORAGE QUOTA') }
        assert message

        cleanup:
        greenMail.stop()
    }
}
