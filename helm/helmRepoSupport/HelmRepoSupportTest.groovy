import org.jfrog.artifactory.client.model.repository.settings.impl.GenericRepositorySettingsImpl
import spock.lang.Specification

import static org.jfrog.artifactory.client.ArtifactoryClient.create

class HelmRepoSupportTest extends Specification {
    def 'Helm repo test'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = create(baseurl, 'admin', 'password')
        def builder = artifactory.repositories().builders()
        def remoterepo = null
        def remote = builder.remoteRepositoryBuilder().key('helm-remote')
        remote.repositorySettings(new GenericRepositorySettingsImpl())
        remote.url('https://kubernetes-charts.storage.googleapis.com')
        artifactory.repositories().create(0, remote.build())
        remoterepo = artifactory.repository('helm-remote')
        def remotepath = 'index.yaml'
        def streamhandle
        when:
        streamhandle = remoterepo.download(remotepath).doDownload().text

        then:
        streamhandle.contains(baseurl)

        cleanup:
        remoterepo?.delete()
    }
}
