import spock.lang.Specification
import org.apache.http.client.HttpResponseException
import org.jfrog.artifactory.client.model.repository.settings.impl.GenericRepositorySettingsImpl

import static org.jfrog.artifactory.client.ArtifactoryClient.create

class DeleteByPropertyValueTest extends Specification {
    def 'delete by property value test'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = create(baseurl, 'admin', 'password')
        def builder = artifactory.repositories().builders().localRepositoryBuilder()
        builder.key('cleanup-local')
        builder.repositorySettings(new GenericRepositorySettingsImpl())
        artifactory.repositories().create(0, builder.build())
        def repo = artifactory.repository('cleanup-local')
        def somefile = new ByteArrayInputStream('content'.bytes)
        def otherfile = new ByteArrayInputStream('content'.bytes)
        repo.upload('somefile', somefile).withProperty('someprop', '4').doUpload()
        repo.upload('otherfile', otherfile).withProperty('someprop', '6').doUpload()
        def plugin = artifactory.plugins().execute('deleteByPropertyValue')
        plugin.withParameter('propertyName', 'someprop')
        plugin.withParameter('propertyValue', '5')
        plugin.withParameter('repo', 'cleanup-local').sync()

        when:
        repo.file('somefile').info()

        then:
        thrown(HttpResponseException)

        when:
        repo.file('otherfile').info()

        then:
        notThrown(HttpResponseException)

        cleanup:
        repo?.delete()
    }
}
