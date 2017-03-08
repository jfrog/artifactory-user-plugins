import spock.lang.Specification

import org.jfrog.artifactory.client.model.repository.settings.impl.MavenRepositorySettingsImpl
import static org.jfrog.artifactory.client.ArtifactoryClient.create

class RemoteDownloadTest extends Specification {
    def 'remote download plugin test'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = create(baseurl, 'admin', 'password')

        def builder = artifactory.repositories().builders()
        def local = builder.localRepositoryBuilder().key('maven-local')
        .repositorySettings(new MavenRepositorySettingsImpl()).build()
        artifactory.repositories().create(0, local)

        when:
        "curl -XPOST -uadmin:password -T ./src/test/groovy/RemoteDownloadTest/conf.json http://localhost:8088/artifactory/api/plugins/execute/remoteDownload".execute().waitFor()

        then:
        artifactory.repository('maven-local').file('my/new/path/artifactory.png').info()

        cleanup:
        artifactory.repository('maven-local').delete()
    }
}
