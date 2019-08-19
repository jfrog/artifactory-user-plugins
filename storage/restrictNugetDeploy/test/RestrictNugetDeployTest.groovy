import spock.lang.Specification
import org.jfrog.artifactory.client.model.repository.settings.impl.NugetRepositorySettingsImpl
import groovy.util.XmlParser
import groovy.xml.XmlUtil
import org.jfrog.artifactory.client.ArtifactoryClientBuilder
import org.apache.http.client.HttpResponseException

class RestrictNugetDeployTest extends Specification {
    def 'restrict nuget deploy test'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = ArtifactoryClientBuilder.create().setUrl(baseurl)
            .setUsername('admin').setPassword('password').build()

        def builder = artifactory.repositories().builders()
        def local = builder.localRepositoryBuilder().key('nuget-local')
        .repositorySettings(new NugetRepositorySettingsImpl()).build()
        artifactory.repositories().create(0, local)

        def far = builder.localRepositoryBuilder().key('nuget-far')
        .repositorySettings(new NugetRepositorySettingsImpl()).build()
        artifactory.repositories().create(0, far)

        def remote = builder.remoteRepositoryBuilder().key('nuget-remote').username('admin').password('password')
        remote.repositorySettings(new NugetRepositorySettingsImpl())
        remote.url('http://localhost:8088/artifactory/api/nuget/nuget-far')
        artifactory.repositories().create(0, remote.build())
        // TODO when creating a nuget repository via the REST API, the
        // feedContextPath and downloadContextPath properties do not get set,
        // even when they are provided. remove this hack whenever this gets fixed
        def conn = new URL("$baseurl/api/system/configuration").openConnection()
        conn.requestMethod = 'GET'
        conn.setRequestProperty('Authorization', "Basic ${'admin:password'.bytes.encodeBase64()}")
        def xml = new XmlParser().parse(conn.inputStream)
        assert conn.responseCode == 200
        conn.disconnect()
        def nugetremote = xml.remoteRepositories[0].remoteRepository.find { it.key.text() == 'nuget-remote' }
        def nuget = nugetremote.nuget
        if (nuget) {
            nuget[0].feedContextPath[0].value = ''
            nuget[0].downloadContextPath[0].value = 'Download'
        } else {
            def instxt = '<nuget xmlns="' + (xml.name() =~ '^\\{(.*)\\}config$')[0][1]
            instxt += '" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">'
            instxt += '<feedContextPath></feedContextPath>'
            instxt += '<downloadContextPath>Download</downloadContextPath></nuget>'
            def ins = new XmlParser().parseText(instxt)
            nugetremote.children().add(1 + nugetremote.findIndexOf { it.name().toString().contains('rejectInvalidJars') }, ins)
        }
        conn = new URL("$baseurl/api/system/configuration").openConnection()
        conn.requestMethod = 'POST'
        conn.doOutput = true
        conn.setRequestProperty('Authorization', "Basic ${'admin:password'.bytes.encodeBase64()}")
        conn.setRequestProperty('Content-Type', 'application/xml')
        conn.outputStream << XmlUtil.serialize(xml)
        assert conn.responseCode == 200
        conn.disconnect()

        def jquery = new File('./src/test/groovy/RestrictNugetDeployTest/jquery.3.1.1.nupkg')
        artifactory.repository('nuget-far').upload('jQuery%20Foundation,%20Inc./jQuery/jQuery.3.1.1.nupkg', jquery).doUpload()

        when:
        def resp = artifactory.repository('nuget-local').upload('jQuery%20Foundation,%20Inc./jQuery/jQuery.3.1.1.nupkg', jquery).doUpload()

        then:
        thrown(HttpResponseException)

        when:
        resp = artifactory.repository('nuget-local').upload('jQuery%20Foundation,%20Inc./new/new.3.1.1.nupkg', jquery).doUpload()

        then:
        resp.size > 0

        cleanup:
        artifactory.repository('nuget-local').delete()
        artifactory.repository('nuget-remote').delete()
        artifactory.repository('nuget-far').delete()
    }
}
