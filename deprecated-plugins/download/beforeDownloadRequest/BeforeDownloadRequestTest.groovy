import spock.lang.Specification
import groovy.json.*

import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import org.jfrog.artifactory.client.ArtifactoryClientBuilder

import org.jfrog.artifactory.client.model.repository.settings.impl.MavenRepositorySettingsImpl

class BeforeDownloadRequestTest extends Specification {
    def 'before download request test'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = ArtifactoryClientBuilder.create().setUrl(baseurl)
            .setUsername('admin').setPassword('password').build()

        def builder = artifactory.repositories().builders()

        // Create a local repository for testing
        def local = builder.localRepositoryBuilder().key('test-local')
        local.repositorySettings(new MavenRepositorySettingsImpl())
        artifactory.repositories().create(0, local.build())

        // Create a remote repository and link it to the local one
        def remote = builder.remoteRepositoryBuilder().key('test-remote')
        remote.repositorySettings(new MavenRepositorySettingsImpl())
        remote.url('http://localhost:8088/artifactory/test-local').username('admin').password('password')
        artifactory.repositories().create(0, remote.build())

        // Get the repos
        def remoterepo = artifactory.repository('test-remote')
        def localrepo = artifactory.repository('test-local')

        // Make a json file and upload it
        localrepo.upload("test.json", new ByteArrayInputStream('{ "name": "John Doe" }'.bytes)).doUpload();

        // Download the file from the remote repo
        def filepath = 'test.json'
        remoterepo.download(filepath).doDownload().text

        // Get checksum for cached file after download and store the value
        def remote_cache_file = artifactory.repository('test-remote-cache').file(filepath).info();
        def checksum_first_cache = remote_cache_file.getChecksums().getMd5()

        // Get checksum for uploaded file and store it
        def first_file = artifactory.repository('test-local').file(filepath).info();
        def checksum_first_file = first_file.getChecksums().getMd5()

        // Let's create another json file with different md5 and upload it
        localrepo.upload("test.json", new ByteArrayInputStream('{ "name": "John Roe" }'.bytes)).doUpload();

        // Download the new file from the remote repo
        remoterepo.download(filepath).doDownload().text

        // Get checksum for new cached file, should be the same as the first one since the file shouldn't have expired yet
        def second_remote_cache_file = artifactory.repository('test-remote-cache').file(filepath).info();
        def checksum_second_cache = second_remote_cache_file.getChecksums().getMd5()

        // Get checksum for uploaded file, this one should be different
        def second_file = artifactory.repository('test-local').file(filepath).info();
        def checksum_second_file = second_file.getChecksums().getMd5()



        when:

        // wait necessary time for file to expire
        sleep(10000)

        // Download again the uploaded file and get the checksum of the cache again, this time it should have changed
        remoterepo.download(filepath).doDownload().text
        def new_second_remote_cache_file = artifactory.repository('test-remote-cache').file(filepath).info();
        def new_checksum_second_cache = new_second_remote_cache_file.getChecksums().getMd5()

        then:
        assert checksum_first_file != checksum_second_file
        assert checksum_first_file == checksum_first_cache
        assert checksum_first_cache == checksum_second_cache
        assert checksum_first_cache != new_checksum_second_cache

        cleanup:
        artifactory.repository('test-remote').delete()
        artifactory.repository('test-local').delete()


    }
}
