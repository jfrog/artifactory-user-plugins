import groovyx.net.http.HttpResponseException
import spock.lang.Specification

import org.jfrog.artifactory.client.model.repository.settings.impl.GenericRepositorySettingsImpl
import static org.jfrog.artifactory.client.ArtifactoryClient.create

class RestrictOverwriteTest extends Specification {
    def 'restrict overwrite test'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = create(baseurl, 'admin', 'password')
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
        resp.checksums == null

        cleanup:
        localrepo?.delete()
    }
}
