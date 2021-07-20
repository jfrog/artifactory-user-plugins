import spock.lang.Specification

import org.jfrog.artifactory.client.model.repository.settings.impl.GenericRepositorySettingsImpl
import org.jfrog.artifactory.client.ArtifactoryClientBuilder

class WhitesourceTest extends Specification {
    def 'whitesource storage test'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = ArtifactoryClientBuilder.create().setUrl(baseurl)
            .setUsername('admin').setPassword('password').build()
        def settings = new GenericRepositorySettingsImpl()
        def builders = artifactory.repositories().builders()
        def repobuilder = builders.localRepositoryBuilder()
        repobuilder.key('whitesource-test-local').repositorySettings(settings)
        artifactory.repositories().create(0, repobuilder.build())
        def repo = artifactory.repository('whitesource-test-local')

        when:
        def desc = "Guava is a suite of core and expanded libraries that"
        desc += " include\n    utility classes, google's collections, io"
        desc += " classes, and much\n    much more.\n\n    Guava has two code"
        desc += " dependencies - javax.annotation\n    per the JSR-305 spec"
        desc += " and javax.inject per the JSR-330 spec."
        def homepage = "http://code.google.com/p/guava-libraries/guava"
        def file = new File('./src/test/groovy/WhitesourceTest/guava-15.0.jar')
        repo.upload('/guava-15.0.jar', file).doUpload()

        then:
        def props = repo.file('/guava-15.0.jar').getProperties('WSS-Description', 'WSS-Homepage', 'WSS-Licenses')
        props['WSS-Description'].contains(':guava-15.0.jar:') || (
            props['WSS-Description'].contains(desc) &&
            props['WSS-Homepage'].contains(homepage) &&
            props['WSS-Licenses'].contains('Apache 2.0')
        )

        cleanup:
        artifactory.repository('whitesource-test-local').delete()
    }
}
