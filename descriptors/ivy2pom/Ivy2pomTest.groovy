import groovy.xml.MarkupBuilder
import org.jfrog.artifactory.client.model.builder.impl.RepositoryBuildersImpl
import spock.lang.Specification

import static org.jfrog.artifactory.client.ArtifactoryClient.create

class Ivy2pomTest extends Specification {
    def 'simple ivy to pom plugin test'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = create(baseurl, 'admin', 'password')
        def builder = RepositoryBuildersImpl.create()
        def ivy = builder.localRepositoryBuilder().key('ivy').repoLayoutRef('ivy-default').build()
        artifactory.repositories().create(0, ivy)
        def ivypath = 'myorg/mymodule/2.0/nulls/ivy-2.0.xml'
        def pompath = 'myorg/mymodule/2.0/mymodule-2.0.pom'
        def xml = new StringWriter()
        new MarkupBuilder(xml).'ivy-module'(version: 2.0) {
            info(organisation: "myorg", module: "mymodule", revision: 2.0)
        }
        artifactory.repository('ivy').upload(ivypath, new ByteArrayInputStream(xml.toString().bytes)).doUpload()

        when:
        def pomfile = new XmlParser().parse(artifactory.repository('ext-release-local').download(pompath).doDownload())

        then:
        pomfile.groupId.text() == 'myorg'
        pomfile.artifactId.text() == 'mymodule'
        pomfile.version.text() == '2.0'

        cleanup:
        artifactory.repository('ext-release-local').delete('myorg')
        artifactory.repository('ivy').delete()
    }
}
