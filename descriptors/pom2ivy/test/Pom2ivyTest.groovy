import spock.lang.Specification
import org.jfrog.artifactory.client.model.repository.settings.impl.MavenRepositorySettingsImpl
import org.jfrog.artifactory.client.model.repository.settings.impl.IvyRepositorySettingsImpl
import org.jfrog.lilypad.Control

import org.jfrog.artifactory.client.ArtifactoryClientBuilder

class Pom2ivyTest extends Specification {
    def 'simple pom to ivy plugin test'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = ArtifactoryClientBuilder.create().setUrl(baseurl)
            .setUsername('admin').setPassword('password').build()

        // ant jars need to be in the root Artifactory lib directory
        moveAntJars()

        def builder = artifactory.repositories().builders()
        def ivy = builder.localRepositoryBuilder().key('ivy-local')
        .repositorySettings(new IvyRepositorySettingsImpl())
        .repoLayoutRef('ivy-default').build()
        artifactory.repositories().create(0, ivy)

        def pom = builder.localRepositoryBuilder().key('pom-local')
        .repositorySettings(new MavenRepositorySettingsImpl()).build()
        artifactory.repositories().create(0, pom)

        def ivypath = 'com.mycompany.app/my-app/1.0/nulls/ivy-1.0.xml'
        def pompath = 'com/mycompany/app/my-app/1.0/my-app-1.0.pom'
        def xml = "<project>"
        xml += "<modelVersion>4.0.0</modelVersion>"
        xml += "<groupId>com.mycompany.app</groupId>"
        xml += "<artifactId>my-app</artifactId>"
        xml += "<version>1.0</version>"
        xml += "</project>"
        artifactory.repository('pom-local').upload(pompath, new ByteArrayInputStream(xml.bytes)).doUpload()

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

    private moveAntJars() {
        def src = './src/test/groovy/Pom2ivyTest/'
        def dst1 = '/opt/jfrog/artifactory/app/artifactory/tomcat/webapps/artifactory/WEB-INF/lib/'
        def dst2 = '/opt/jfrog/artifactory/tomcat/webapps/artifactory/WEB-INF/lib/'
        def jar1 = 'ant-1.8.3.jar', jar2 = 'ant-launcher-1.8.3.jar'
        try {
            Control.setFileContent(8088, dst1 + jar1, new File(src + jar1))
            Control.setFileContent(8088, dst1 + jar2, new File(src + jar2))
        } catch (Exception ex) {
            Control.setFileContent(8088, dst2 + jar1, new File(src + jar1))
            Control.setFileContent(8088, dst2 + jar2, new File(src + jar2))
        }
        Control.stop(8088)
        Control.resume(8088)
        System.sleep(8000)
    }
}
