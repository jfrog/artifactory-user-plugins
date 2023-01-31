import spock.lang.Specification

import org.jfrog.artifactory.client.model.repository.settings.impl.MavenRepositorySettingsImpl
import org.jfrog.artifactory.client.ArtifactoryClientBuilder

class RemoteDownloadTest extends Specification {
    def 'remote download plugin test'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = ArtifactoryClientBuilder.create().setUrl(baseurl)
            .setUsername('admin').setPassword('password').build()

        def builder = artifactory.repositories().builders()
        def source = builder.localRepositoryBuilder().key('source-local')
        source.repositorySettings(new MavenRepositorySettingsImpl())
        artifactory.repositories().create(0, source.build())
        def sourcerepo = artifactory.repository('source-local')
        def png = new File('./src/test/groovy/RemoteDownloadTest/googlelogo_color_272x92dp.png')
        sourcerepo.upload('images/branding/googlelogo/1x/googlelogo_color_272x92dp.png', png).doUpload();
        def local = builder.localRepositoryBuilder().key('maven-local')
        .repositorySettings(new MavenRepositorySettingsImpl()).build()
        artifactory.repositories().create(0, local)

        when:
        "curl -XPOST -uadmin:password -T ./src/test/groovy/RemoteDownloadTest/conf.json http://localhost:8088/artifactory/api/plugins/execute/remoteDownload".execute().waitFor()

        then:
        artifactory.repository('maven-local').file('my/new/path/image.png').info()

        cleanup:
        artifactory.repository('source-local').delete()
        artifactory.repository('maven-local').delete()
    }
}
