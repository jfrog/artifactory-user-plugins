import groovyx.net.http.HttpResponseException
import groovy.json.JsonSlurper
import org.jfrog.artifactory.client.model.PackageType
import org.jfrog.artifactory.client.model.builder.impl.RepositoryBuildersImpl
import spock.lang.Specification

import static org.jfrog.artifactory.client.ArtifactoryClient.create

class YumCalculateAsyncTest extends Specification {
    def 'asynchronous yum calculate test'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = create(baseurl, 'admin', 'password')
        def builder = RepositoryBuildersImpl.create()
        builder = builder.localRepositoryBuilder().key('yum')
        builder = builder.packageType(PackageType.yum)
        builder = builder.yumRootDepth(2).calculateYumMetadata(false)
        def yum = builder.build()
        artifactory.repositories().create(0, yum)
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
        resp2 == resp3
        resp.contains('done')

        cleanup:
        artifactory.repository('yum').delete()
    }
}
