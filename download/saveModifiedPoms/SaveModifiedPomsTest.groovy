import spock.lang.Specification
import org.apache.http.client.HttpResponseException
import org.jfrog.artifactory.client.model.repository.settings.impl.GenericRepositorySettingsImpl

import org.jfrog.artifactory.client.ArtifactoryClientBuilder

class SaveModifiedPomsTest extends Specification {
    def 'save modified poms test'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = ArtifactoryClientBuilder.create().setUrl(baseurl)
            .setUsername('admin').setPassword('password').build()
        def builder = artifactory.repositories().builders()
        def reposource = builder.localRepositoryBuilder().key('source-repo')
        reposource.repositorySettings(new GenericRepositorySettingsImpl())
        def repooverride = builder.localRepositoryBuilder().key('fmw-virtual')
        repooverride.repositorySettings(new GenericRepositorySettingsImpl())
        def repobackup = builder.localRepositoryBuilder().key('saved-poms')
        repobackup.repositorySettings(new GenericRepositorySettingsImpl())
        artifactory.repositories().create(0, reposource.build())
        artifactory.repositories().create(0, repooverride.build())
        artifactory.repositories().create(0, repobackup.build())
        def source = artifactory.repository('source-repo')
        def override = artifactory.repository('fmw-virtual')
        def backup = artifactory.repository('saved-poms')

        when:
        def original1 = new ByteArrayInputStream('original1'.bytes)
        def original2 = new ByteArrayInputStream('original2'.bytes)
        def updated1 = new ByteArrayInputStream('updated1'.bytes)
        source.upload('original1.pom', original1).doUpload()
        source.upload('original2.pom', original2).doUpload()
        override.upload('original1.pom', updated1).doUpload()
        artifactory.plugins().execute('testCopyModifiedPoms').sync()

        then:
        backup.download('original1.pom').doDownload().text == 'updated1'
        { it ->
            try { backup.download('original2.pom').doDownload(); false }
            catch (HttpResponseException ex) { true }
        }.call()

        cleanup:
        source?.delete()
        override?.delete()
        backup?.delete()
    }
}
