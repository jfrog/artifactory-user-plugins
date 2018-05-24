import spock.lang.Specification
import org.jfrog.artifactory.client.model.repository.settings.impl.MavenRepositorySettingsImpl
import org.jfrog.artifactory.client.ArtifactoryClientBuilder
import org.apache.http.client.HttpResponseException

class PgpVerifyTest extends Specification {
    def 'pgp verify test'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = ArtifactoryClientBuilder.create().setUrl(baseurl)
            .setUsername('admin').setPassword('password').build()

        def builder = artifactory.repositories().builders()
        def local = builder.localRepositoryBuilder().key('maven-local')
        .repositorySettings(new MavenRepositorySettingsImpl()).build()
        artifactory.repositories().create(0, local)

        def xmlfile = new File('./src/test/groovy/PgpVerifyTest/maven-metadata.xml')
        def ascfile = new File('./src/test/groovy/PgpVerifyTest/maven-metadata.xml.asc')
        def badfile = new File('./src/test/groovy/PgpVerifyTest/bad-maven-metadata.xml.asc')

        artifactory.repository('maven-local').upload('foo/maven-metadata.xml', xmlfile).doUpload()
        artifactory.repository('maven-local').upload('foo/maven-metadata.xml.asc', ascfile).doUpload()
        artifactory.repository('maven-local').upload('bar/maven-metadata.xml', xmlfile).doUpload()
        artifactory.repository('maven-local').upload('bar/maven-metadata.xml.asc', badfile).doUpload()

        when:
        artifactory.repository('maven-local').download('foo/maven-metadata.xml').doDownload()

        then:
        notThrown(HttpResponseException)

        when:
        artifactory.repository('maven-local').download('bar/maven-metadata.xml').doDownload()

        then:
        thrown(HttpResponseException)

        cleanup:
        artifactory.repository('maven-local').delete()
    }
}
