import spock.lang.Specification

import org.jfrog.artifactory.client.model.repository.settings.impl.MavenRepositorySettingsImpl

import static org.jfrog.artifactory.client.ArtifactoryClient.create

class LayoutPropertiesTest extends Specification {
    def 'maven layout properties plugin test'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = create(baseurl, 'admin', 'password')

        def builder = artifactory.repositories().builders()
        def local = builder.localRepositoryBuilder().key('maven-local')
        .repositorySettings(new MavenRepositorySettingsImpl()).build()
        artifactory.repositories().create(0, local)

        def path = 'org/test/modname/1.0/modname-1.0.txt'
        def repo = 'maven-local'

        when:
        def stream = new ByteArrayInputStream('test'.getBytes('utf-8'))
        artifactory.repository(repo).upload(path, stream).doUpload()
        def props = artifactory.repository(repo).file(path).getProperties('')

        then:
        props['layout.organization'] == ['org.test']
        props['layout.module'] == ['modname']
        props['layout.baseRevision'] == ['1.0']

        cleanup:
        artifactory.repository(repo)
    }
}
