import spock.lang.Specification

import org.jfrog.artifactory.client.model.repository.settings.impl.GenericRepositorySettingsImpl
import org.jfrog.artifactory.client.ArtifactoryClientBuilder
import org.apache.http.client.HttpResponseException

class RestrictOverwriteTest extends Specification {
    def 'restrict overwrite test'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = ArtifactoryClientBuilder.create().setUrl(baseurl)
            .setUsername('admin').setPassword('password').build()
        def builder = artifactory.repositories().builders()
        def local = builder.localRepositoryBuilder().key('generic-local')
        local.repositorySettings(new GenericRepositorySettingsImpl())
        artifactory.repositories().create(0, local.build())
        def localrepo = artifactory.repository('generic-local')
        def is1 = new ByteArrayInputStream('test1'.getBytes('utf-8'))
        localrepo.upload('some/path/file', is1).doUpload()

        when:
        def is2 = new ByteArrayInputStream('test2'.getBytes('utf-8'))
        def resp = localrepo.upload('some/path/file', is2).doUpload()

        then:
        thrown(HttpResponseException)

        cleanup:
        localrepo?.delete()
    }
}
