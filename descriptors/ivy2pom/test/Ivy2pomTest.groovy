import spock.lang.Specification
import org.jfrog.artifactory.client.model.repository.settings.impl.MavenRepositorySettingsImpl
import org.jfrog.artifactory.client.model.repository.settings.impl.IvyRepositorySettingsImpl
import org.jfrog.lilypad.Control

import org.jfrog.artifactory.client.ArtifactoryClientBuilder

class Ivy2pomTest extends Specification {
    def 'simple ivy to pom plugin test'() {
        setup:
        // ant jars need to be in the root Artifactory lib directory
        moveAntJars()

        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = ArtifactoryClientBuilder.create().setUrl(baseurl)
            .setUsername('admin').setPassword('password').build()

        def builder = artifactory.repositories().builders()
        def ivy = builder.localRepositoryBuilder().key('ivy-local')
        .repositorySettings(new IvyRepositorySettingsImpl())
        .repoLayoutRef('ivy-default').build()
        artifactory.repositories().create(0, ivy)

        def pom = builder.localRepositoryBuilder().key('pom-local')
        .repositorySettings(new MavenRepositorySettingsImpl()).build()
        artifactory.repositories().create(0, pom)

        def ivypath = 'myorg/mymodule/2.0/nulls/ivy-2.0.xml'
        def pompath = 'myorg/mymodule/2.0/mymodule-2.0.pom'
        def xml = "<ivy-module version='2.0'>"
        xml += "<info organisation='myorg' module='mymodule' revision='2.0' />"
        xml += "</ivy-module>"
        artifactory.repository('ivy-local').upload(ivypath, new ByteArrayInputStream(xml.bytes)).doUpload()

        when:
        def pomfile = new XmlParser().parse(artifactory.repository('pom-local').download(pompath).doDownload())

        then:
        pomfile.groupId.text() == 'myorg'
        pomfile.artifactId.text() == 'mymodule'
        pomfile.version.text() == '2.0'

        cleanup:
        artifactory.repository('pom-local').delete()
        artifactory.repository('ivy-local').delete()
    }

    private moveAntJars() {
        def src = './src/test/groovy/Ivy2pomTest/'
        def dst = '/opt/jfrog/artifactory/app/artifactory/tomcat/webapps/artifactory/WEB-INF/lib/'
        def jar1 = 'ant-1.8.3.jar', jar2 = 'ant-launcher-1.8.3.jar'
        try {
            Control.setFileContent(8088, dst + jar1, new File(src + jar1))
            Control.setFileContent(8088, dst + jar2, new File(src + jar2))
        } catch (Exception ex) {
            dst = '/opt/jfrog/artifactory/tomcat/webapps/artifactory/WEB-INF/lib/'
            Control.setFileContent(8088, dst + jar1, new File(src + jar1))
            Control.setFileContent(8088, dst + jar2, new File(src + jar2))
        }
        Control.stop(8088)
        Control.resume(8088)
        System.sleep(8000)
    }
}
