import groovyx.net.http.HttpResponseException
import spock.lang.Specification

import static org.jfrog.artifactory.client.ArtifactoryClient.create

class DiscoverLicenseAndPreventUnapprovedTest extends Specification {
    def 'discover license and prevent unapproved test'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def auth = "Basic ${'admin:password'.bytes.encodeBase64().toString()}"
        def artifactory = create(baseurl, 'admin', 'password')
        def repo = artifactory.repository('libs-release-local')

        when:
        // set up file
        def fileurl = "/libs-release-local/$status;artifactory.licenses=$license"
        def request = new URL(baseurl + fileurl).openConnection()
        request.setDoOutput(true)
        request.setRequestMethod('PUT')
        request.setRequestProperty('Authorization', auth)
        request.getOutputStream().write("$status license".bytes)
        request.getResponseCode()
        if (approved) {
            repo.file(status).deleteProperty('approve.status')
            repo.file(status).properties().addProperty('approve.status', status).doSet()
        }

        then:
        // ensure the properties are correctly set
        def licenseVal = recognized ? license : 'Not Found'
        repo.file(status).getPropertyValues('artifactory.licenses').contains(licenseVal)
        repo.file(status).getPropertyValues('approve.status').contains(status)
        // ensure only the approved files are accessible
        testDownload(repo, status, "$status license", approved)

        cleanup:
        repo.delete(status)

        where:
        status     | license   | approved | recognized
        'approved' | 'GPL-3.0' | true     | true
        'rejected' | 'GPL-2.0' | false    | true
        'pending'  | 'WTFPL'   | false    | false
    }

    def testDownload(repo, status, content, approved) {
        try {
            repo.download(status).doDownload().text == content && approved
        } catch (HttpResponseException ex) {
            !approved
        }
    }
}
