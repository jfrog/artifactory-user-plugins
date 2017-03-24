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
        def ivy = builder.localRepositoryBuilder().key('ivy-local').repoLayoutRef('ivy-default').build()
        artifactory.repositories().create(0, ivy)
        
        def pom = builder.localRepositoryBuilder().key('pom-local').repoLayoutRef('maven-2-default').build()
        artifactory.repositories().create(0, pom)
        
        def ivypath = 'com.mycompany.app/my-app/1.0/nulls/ivy-1.0.xml'
        def pompath = 'com/mycompany/app/my-app/1.0/my-app-1.0.pom'
        
        def xml = new StringWriter()
        new MarkupBuilder(xml).project() {
            modelVersion('4.0.0')
            groupId('com.mycompany.app')
            artifactId('my-app')
            version(1.0)
        }
        artifactory.repository('pom-local').upload(pompath, new ByteArrayInputStream(xml.toString().bytes)).doUpload()

        when:
        def ivyfile = new XmlParser().parse(artifactory.repository('ivy-local').download(ivypath).doDownload())

        then:
        ivyfile.info[0].@organisation == 'com.mycompany.app'
        ivyfile.info[0].@module == 'my-app'
        ivyfile.info[0].@revision == '1.0'

        cleanup:
        artifactory.repository('ivy-local').delete()
        artifactory.repository('pom-local').delete()
    }
}
