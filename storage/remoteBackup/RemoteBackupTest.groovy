import spock.lang.Specification
import org.jfrog.artifactory.client.model.repository.settings.impl.MavenRepositorySettingsImpl

import org.jfrog.artifactory.client.ArtifactoryClientBuilder

class RemoteBackupTest extends Specification {
    def 'remote backup test'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = ArtifactoryClientBuilder.create().setUrl(baseurl)
            .setUsername('admin').setPassword('password').build()
        def builder = artifactory.repositories().builders()
        def localrepo = null, remoterepo = null

        when:
        def remote = builder.remoteRepositoryBuilder().key('backup-remote')
        remote.repositorySettings(new MavenRepositorySettingsImpl())
        remote.url('https://jcenter.bintray.com')
        artifactory.repositories().create(0, remote.build())
        remoterepo = artifactory.repository('backup-remote')
        def remotepath1 = 'junit/junit/3.8.1/junit-3.8.1.pom'
        remoterepo.download(remotepath1).doDownload().text
        def local = builder.localRepositoryBuilder().key('backup-local')
        local.repositorySettings(new MavenRepositorySettingsImpl())
        artifactory.repositories().create(0, local.build())
        localrepo = artifactory.repository('backup-local')
        def remotepath2 = 'xmlpull/xmlpull/1.1.3.1/xmlpull-1.1.3.1.pom'
        remoterepo.download(remotepath2).doDownload().text

        then:
        localrepo.file(remotepath2).info()

        when:
        artifactory.plugins().execute('remoteBackup').sync()

        then:
        localrepo.file(remotepath1).info()

        cleanup:
        remoterepo?.delete()
        localrepo?.delete()
    }
}
