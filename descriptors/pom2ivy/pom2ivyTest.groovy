import groovy.xml.MarkupBuilder
import org.jfrog.artifactory.client.model.builder.impl.RepositoryBuildersImpl
import spock.lang.Specification

import static org.jfrog.artifactory.client.ArtifactoryClient.create

class Pom2ivyTest extends Specification {
    def 'simple pom to ivy plugin test'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = create(baseurl, 'admin', 'password')
        def builder = RepositoryBuildersImpl.create()
        artifactory.repository('ext-release-local').delete()
        def ivy = builder.localRepositoryBuilder().key('ext-release-local').repoLayoutRef('ivy-default').build()
        artifactory.repositories().create(0, ivy)
        def maven = builder.localRepositoryBuilder().key('maven').repoLayoutRef('maven-2-default').build()
        artifactory.repositories().create(0, maven)
        def ivypath = 'com.mycompany.app/my-app/1.0/nulls/ivy-1.0.xml'
        def pompath = 'com/mycompany/app/my-app/1.0/my-app-1.0.pom'
        def xml = new StringWriter()
        new MarkupBuilder(xml).project() {
            modelVersion('4.0.0')
            groupId('com.mycompany.app')
            artifactId('my-app')
            version(1.0)
        }
        artifactory.repository('maven').upload(pompath, new ByteArrayInputStream(xml.toString().bytes)).doUpload()

        when:
        def ivyfile = new XmlParser().parse(artifactory.repository('ext-release-local').download(ivypath).doDownload())

        then:
        ivyfile.info[0].@organisation == 'com.mycompany.app'
        ivyfile.info[0].@module == 'my-app'
        ivyfile.info[0].@revision == '1.0'

        cleanup:
        builder = RepositoryBuildersImpl.create()
        artifactory.repository('ext-release-local').delete()
        def keep = builder.localRepositoryBuilder().key('ext-release-local').repoLayoutRef('maven-2-default').build()
        artifactory.repositories().create(0, keep)
        artifactory.repository('maven').delete()
    }
}
