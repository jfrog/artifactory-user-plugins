import groovy.json.JsonSlurper
import org.jfrog.artifactory.client.model.PackageType
import org.jfrog.artifactory.client.model.repository.settings.impl.YumRepositorySettingsImpl
import spock.lang.Specification

import org.jfrog.artifactory.client.ArtifactoryClientBuilder

class YumCalculateAsyncTest extends Specification {
    def 'asynchronous yum calculate test'() {
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

        when:
        def resp = null
        def resp1 = artifactory.plugins().execute('yumCalculateAsync').withParameter('path', 'yum/org/mod1').sync()
        def resp2 = artifactory.plugins().execute('yumCalculateAsync').withParameter('path', 'yum/org/mod1').sync()
        def resp3 = artifactory.plugins().execute('yumCalculateAsync').withParameter('path', 'yum/org/mod1').sync()
        def uid = new JsonSlurper().parseText(resp3)['uid']
        for (i in 1..60) {
            resp = artifactory.plugins().execute('yumCalculateQuery').withParameter('uid', uid).sync()
            if (resp.contains('done')) break
            sleep(1000)
        }

        then:
        artifactory.repository('yum').folder('org/mod1/repodata').info()
        resp1 != resp2
        resp.contains('done')

        cleanup:
        artifactory.repository('yum').delete()
    }
}
