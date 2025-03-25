import org.apache.http.client.HttpResponseException
import spock.lang.Specification

import org.jfrog.artifactory.client.model.repository.settings.impl.MavenRepositorySettingsImpl
import org.jfrog.artifactory.client.ArtifactoryClientBuilder

class RlBlockTest extends Specification {

    def 'rl block test'() {
        // ----------------------------------------------------------
        // create a custom local repo where we will run our test
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def testreponame = 'maven-local'
        def mytestproperty = 'RL.scan-status'

        def artifactory = ArtifactoryClientBuilder
            .create()
            .setUrl(baseurl)
            .setUsername('admin')
            .setPassword('password')
            .build()

        def builder = artifactory
            .repositories()
            .builders()

        def local = builder
            .localRepositoryBuilder()
            .key(testreponame)
            .repositorySettings(new MavenRepositorySettingsImpl())
            .build()

        artifactory
            .repositories()
            .create(0, local)

        // ----------------------------------------------------------
        // create a artifact with a property and a value
        when:
        def artifact = new ByteArrayInputStream("$status artifact".bytes)

        artifactory
            .repository(testreponame)
            .upload(status, artifact) // status is the filename we use here, artifact is the content of the file
            .doUpload()

        artifactory
            .repository(testreponame)
            .file(status)
            .properties()
            .addProperty(mytestproperty, status) // add a prop with a value
            .doSet()

        // ----------------------------------------------------------
        // after set prop: try download; fails or passes: depending on the property value (approved)
        then:
        testDownloadSimple(
            artifactory.repository(testreponame),
            status,
            "$status artifact",
            approved
        )

        // ----------------------------------------------------------
        // after the test delete the tem repo
        cleanup:
        artifactory.repository(testreponame).delete()

        // ----------------------------------------------------------
        // the dynamic variables we use during the test cycle
        // 2 var names with their values
        where:
        status | approved
        'pass' | true
        'fail' | false
    }

    // our test case
    def testDownloadSimple(repo, status, content, approved) {
        // a simple download oly tests the property value
        try {
            // download the file (status)
            // compare the content text with the expected string '$content'
            repo
                .download(status)
                .doDownload()
                .text == content && approved
        } catch (HttpResponseException ex) {
            !approved
        }
    }

	// later a testcase with allow localhost, but that needs a dynamic properties file

	// later a test case with allow my repo name, but that needs a dynamic properties file
}
