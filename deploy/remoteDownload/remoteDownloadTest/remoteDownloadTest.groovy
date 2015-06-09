import static org.jfrog.artifactory.client.ArtifactoryClient.create
import org.jfrog.artifactory.client.model.PluginType
import spock.lang.Specification

class RemoteDownloadTest extends Specification {
    def 'example remote download plugin test'() {
        setup:
        def artifactory = create("http://localhost:8088/artifactory", "admin", "password")
        // install dependency libraries
        def libdir = new File('./etc/plugins/lib')
        libdir.mkdir()
        def url1 = new URL('http://repo.spring.io/libs-release-remote/org/codehaus/groovy/modules/http-builder/http-builder/0.7.2/http-builder-0.7.2.jar')
        def url2 = new URL('https://bintray.com/artifact/download/bintray/jcenter/net/sf/json-lib/json-lib/2.4/json-lib-2.4-jdk15.jar')
        new File(libdir, 'http-builder-0.7.2.jar').newOutputStream() << url1.openStream()
        new File(libdir, 'json-lib-2.4-jdk15.jar').newOutputStream() << url2.openStream()
        // wait for dependencies to be recognized and plugin to take
        while (!artifactory.plugins().list(PluginType.executions).any { it.name == 'remoteDownload' }) Thread.sleep(1000)

        when:
        "curl -X POST -uadmin:password -T ./src/test/groovy/remoteDownloadTest/conf.json http://localhost:8088/artifactory/api/plugins/execute/remoteDownload".execute().waitFor()

        then:
        artifactory.repository('libs-release-local').file('my/new/path/docker.png').info()

        cleanup:
        artifactory.repository('libs-release-local').delete('my')
        libdir.listFiles().each { it.delete() }
        libdir.delete()
    }
}
