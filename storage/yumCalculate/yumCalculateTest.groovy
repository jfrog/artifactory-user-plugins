import groovyx.net.http.HttpResponseException
import org.jfrog.artifactory.client.model.builder.impl.RepositoryBuildersImpl
import static org.jfrog.artifactory.client.ArtifactoryClient.create
import spock.lang.Specification

class YumCalculateTest extends Specification {
    def 'simple yum calculate test'() {
        setup:
        def artifactory = create("http://localhost:8088/artifactory", "admin", "password")
        def builder = RepositoryBuildersImpl.create()
        def yum = builder.localRepositoryBuilder().key('yum').yumRootDepth(2).calculateYumMetadata(false).build()
        artifactory.repositories().create(0, yum)
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
