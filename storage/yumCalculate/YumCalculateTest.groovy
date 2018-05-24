import org.apache.http.client.HttpResponseException
import spock.lang.Specification

import org.jfrog.artifactory.client.model.repository.settings.impl.YumRepositorySettingsImpl
import org.jfrog.artifactory.client.ArtifactoryClientBuilder

class YumCalculateTest extends Specification {
    def 'simple yum calculate test'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = ArtifactoryClientBuilder.create().setUrl(baseurl)
            .setUsername('admin').setPassword('password').build()

        def builder = artifactory.repositories().builders()
        def settings = new YumRepositorySettingsImpl()
        settings.yumRootDepth = 2
        settings.calculateYumMetadata = false
        def local = builder.localRepositoryBuilder().key('yum')
        .repositorySettings(settings).build()
        artifactory.repositories().create(0, local)

        artifactory.repository('yum').folder('org/mod1').create()
        artifactory.repository('yum').folder('org/mod2').create()

        when:
        artifactory.plugins().execute('yumCalculate').withParameter('path', 'yum/org/mod1').sync()
        artifactory.repository('yum').folder('org/mod2/repodata').info()

        then:
        thrown(HttpResponseException)
        artifactory.repository('yum').folder('org/mod1/repodata').info()

        cleanup:
        artifactory.repository('yum').delete()
    }
}
