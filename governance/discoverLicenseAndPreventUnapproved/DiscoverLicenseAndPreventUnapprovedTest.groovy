import org.apache.http.client.HttpResponseException
import spock.lang.Specification

import org.jfrog.artifactory.client.model.repository.settings.impl.MavenRepositorySettingsImpl

import org.jfrog.artifactory.client.ArtifactoryClientBuilder

class DiscoverLicenseAndPreventUnapprovedTest extends Specification {
    def 'discover license and prevent unapproved test'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def auth = "Basic ${'admin:password'.bytes.encodeBase64().toString()}"
        def artifactory = ArtifactoryClientBuilder.create().setUrl(baseurl)
            .setUsername('admin').setPassword('password').build()

        def builder = artifactory.repositories().builders()
        def local = builder.localRepositoryBuilder().key('maven-local')
        .repositorySettings(new MavenRepositorySettingsImpl()).build()
        artifactory.repositories().create(0, local)

        def repo = artifactory.repository('maven-local')

        when:
        // set up file
        def fileurl = "/maven-local/$status;artifactory.licenses=$license"
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
        def licenseVal1 = recognized ? license : 'Unknown'
        def licenseVal2 = recognized ? license : 'Not Found'
        def licenseVal3 = recognized ? license : 'Not Searched'
        def licenses = repo.file(status).getPropertyValues('artifactory.licenses')
        licenses.contains(licenseVal1) || licenses.contains(licenseVal2) || licenses.contains(licenseVal3)
        repo.file(status).getPropertyValues('approve.status').contains(status)
        // ensure only the approved files are accessible
        testDownload(repo, status, "$status license", approved)

        cleanup:
        repo.delete()

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
