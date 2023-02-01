import spock.lang.Specification
import org.jfrog.artifactory.client.ArtifactoryClientBuilder

import org.jfrog.artifactory.client.model.repository.settings.impl.MavenRepositorySettingsImpl

class CreateCopyTest extends Specification {
    def 'create copy test'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = ArtifactoryClientBuilder.create().setUrl(baseurl)
            .setUsername('admin').setPassword('password').build()
        def file = new File('/home/auser/weatherr_0.1.2.tar.gz')

        def builder1 = artifactory.repositories().builders()
        def local1 = builder1.localRepositoryBuilder().key('maven-local')
        .repositorySettings(new MavenRepositorySettingsImpl()).build()
        artifactory.repositories().create(0, local1)

        def builder2 = artifactory.repositories().builders()
        def local2 = builder2.localRepositoryBuilder().key('maven-copy')
        .repositorySettings(new MavenRepositorySettingsImpl()).build()
        artifactory.repositories().create(0, local2)

        when:
        //create item in local
        artifactory.repository('maven-local')
        .upload('file', new ByteArrayInputStream('test'.getBytes('utf-8')))
        .doUpload()

        then:
        //make sure the item was copied to copy repo with path/info
        artifactory.repository("maven-copy").file("file").info()

        cleanup:
        //delete items
        artifactory.repository("maven-local").delete()
        artifactory.repository("maven-copy").delete()
    }
}
