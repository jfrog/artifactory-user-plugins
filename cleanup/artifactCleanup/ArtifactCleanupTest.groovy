import org.jfrog.artifactory.client.ArtifactoryRequest
import org.jfrog.artifactory.client.impl.ArtifactoryRequestImpl
import org.jfrog.artifactory.client.ArtifactoryClientBuilder

import org.jfrog.artifactory.client.model.repository.settings.impl.MavenRepositorySettingsImpl
import org.jfrog.artifactory.client.model.Privilege
import org.apache.http.client.HttpResponseException
import groovy.json.JsonBuilder
import org.jfrog.lilypad.Control
import spock.lang.Specification

class ArtifactCleanupTest extends Specification {

    private def createClientAndMavenRepo(String repoName){

        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = ArtifactoryClientBuilder.create().setUrl(baseurl)
            .setUsername('admin').setPassword('password').build()

        def builder = artifactory.repositories().builders()
        def repository = builder.localRepositoryBuilder().key(repoName)
            .repositorySettings(new MavenRepositorySettingsImpl()).build()
        artifactory.repositories().create(0, repository)

        return artifactory
    }

    def 'artifact cleanup test'() {
        setup:
        def artifactory = createClientAndMavenRepo('maven-local')

        def file = new ByteArrayInputStream('test'.bytes)
        artifactory.repository('maven-local').upload('test', file).doUpload()

        when:
        artifactory.plugins().execute('cleanup').
            withParameter('repos', 'maven-local').sync()
        artifactory.repository('maven-local').file('test').info()

        then:
        notThrown(HttpResponseException)

        when:
        artifactory.plugins().execute('cleanup').
            withParameter('repos', 'maven-local').
            withParameter('timeUnit', 'month').
            withParameter('timeInterval', '0').sync()
        artifactory.repository('maven-local').file('test').info()

        then:
        thrown(HttpResponseException)

        cleanup:
        artifactory.repository('maven-local').delete()
    }

    def 'artifact cleanup test with dryrun'() {
        setup:
        def artifactory = createClientAndMavenRepo('maven-local')

        def file = new ByteArrayInputStream('test'.bytes)
        artifactory.repository('maven-local').upload('test', file).doUpload()

        when:
        artifactory.plugins().execute('cleanup').
            withParameter('repos', 'maven-local').sync()
        artifactory.repository('maven-local').file('test').info()

        then:
        notThrown(HttpResponseException)

        when:
        artifactory.plugins().execute('cleanup').
            withParameter('repos', 'maven-local').
            withParameter('timeUnit', 'month').
            withParameter('timeInterval', '0').
            withParameter('dryRun', 'true').sync()
        artifactory.repository('maven-local').file('test').info()

        then:
        notThrown(HttpResponseException)

        cleanup:
        artifactory.repository('maven-local').delete()
    }

    def 'artifact cleanup test using deprecated months parameter'() {
        setup:
        def artifactory = createClientAndMavenRepo('maven-local')

        def file = new ByteArrayInputStream('test'.bytes)
        artifactory.repository('maven-local').upload('test', file).doUpload()

        when:
        artifactory.plugins().execute('cleanup').
            withParameter('repos', 'maven-local').sync()
        artifactory.repository('maven-local').file('test').info()

        then:
        notThrown(HttpResponseException)

        when:
        artifactory.plugins().execute('cleanup').
            withParameter('repos', 'maven-local').
            withParameter('months', '0').sync()
        artifactory.repository('maven-local').file('test').info()

        then:
        thrown(HttpResponseException)

        cleanup:
        artifactory.repository('maven-local').delete()
    }

    def 'artifact cleanup skip artifact test'() {
        setup:
        def artifactory = createClientAndMavenRepo('maven-local')

        def file = new ByteArrayInputStream('test'.bytes)
        artifactory.repository('maven-local').upload('test', file).withProperty('cleanup.skip', 'true').doUpload()

        when:
        artifactory.plugins().execute('cleanup').
            withParameter('repos', 'maven-local').
            withParameter('timeUnit', 'month').
            withParameter('timeInterval', '0').sync()
        artifactory.repository('maven-local').file('test').info()

        then:
        notThrown(HttpResponseException)

        when:
        artifactory.plugins().execute('cleanup').
            withParameter('repos', 'maven-local').
            withParameter('timeUnit', 'month').
            withParameter('timeInterval', '0').
            withParameter('disablePropertiesSupport', 'true').sync()
        artifactory.repository('maven-local').file('test').info()

        then:
        thrown(HttpResponseException)

        cleanup:
        artifactory.repository('maven-local').delete()
    }

    def 'artifact cleanup skip folder test'() {
        setup:
        def artifactory = createClientAndMavenRepo('maven-local')

        def file = new ByteArrayInputStream('test'.bytes)
        artifactory.repository('maven-local').upload('foo/bar/test', file).doUpload()

        // Test property on all folders path
        when:
        artifactory.repository('maven-local').folder('foo').properties().addProperty('cleanup.skip', 'true').doSet()
        artifactory.repository('maven-local').folder('foo/bar').properties().addProperty('cleanup.skip', 'true').doSet()
        artifactory.plugins().execute('cleanup').
            withParameter('repos', 'maven-local').
            withParameter('timeUnit', 'month').
            withParameter('timeInterval', '0').sync()
        artifactory.repository('maven-local').file('foo/bar/test').info()

        then:
        notThrown(HttpResponseException)

        // Test property on root folder path
        when:
        artifactory.repository('maven-local').folder('foo').deleteProperty('cleanup.skip')
        artifactory.repository('maven-local').folder('foo/bar').deleteProperty('cleanup.skip')

        artifactory.repository('maven-local').folder('foo').properties().addProperty('cleanup.skip', 'true').doSet()
        artifactory.plugins().execute('cleanup').
            withParameter('repos', 'maven-local').
            withParameter('timeUnit', 'month').
            withParameter('timeInterval', '0').sync()
        artifactory.repository('maven-local').file('foo/bar/test').info()

        then:
        notThrown(HttpResponseException)

        // Test property on parent folder path
        when:
        artifactory.repository('maven-local').folder('foo').deleteProperty('cleanup.skip')
        artifactory.repository('maven-local').folder('foo/bar').deleteProperty('cleanup.skip')

        artifactory.repository('maven-local').folder('foo/bar').properties().addProperty('cleanup.skip', 'true').doSet()
        artifactory.plugins().execute('cleanup').
            withParameter('repos', 'maven-local').
            withParameter('timeUnit', 'month').
            withParameter('timeInterval', '0').sync()
        artifactory.repository('maven-local').file('foo/bar/test').info()

        then:
        notThrown(HttpResponseException)

        when:
        artifactory.plugins().execute('cleanup').
            withParameter('repos', 'maven-local').
            withParameter('timeUnit', 'month').
            withParameter('timeInterval', '0').
            withParameter('disablePropertiesSupport', 'true').sync()
        artifactory.repository('maven-local').file('foo/bar/test').info()

        then:
        thrown(HttpResponseException)

        cleanup:
        artifactory.repository('maven-local').delete()
    }

    def 'artifact cleanup skip same prefix folder test'() {
        setup:
        def artifactory = createClientAndMavenRepo('maven-local')

        def file = new ByteArrayInputStream('test'.bytes)

        artifactory.repository('maven-local').upload('foo/bar/test', file).doUpload()
        artifactory.repository('maven-local').folder('foo').properties().addProperty('cleanup.skip', 'true').doSet()

        // File not skipped : should be deleted
        artifactory.repository('maven-local').upload('foobar/test', file).doUpload()

        when:
        artifactory.plugins().execute('cleanup').
            withParameter('repos', 'maven-local').
            withParameter('timeUnit', 'month').
            withParameter('timeInterval', '0').sync()
        artifactory.repository('maven-local').file('foobar/test').info()

        then:
        thrown(HttpResponseException)

        cleanup:
        artifactory.repository('maven-local').delete()
    }

    def 'artifact cleanup permissions test'() {
        setup:
        def artifactory = createClientAndMavenRepo('maven-local')
        def file = new ByteArrayInputStream('test'.bytes)
        artifactory.repository('maven-local').upload('foo/bar/test', file).doUpload()
        artifactory.repository('maven-local').upload('foo/ban/test', file).doUpload()
        // create permissions
        def group = artifactory.security().builders().groupBuilder().name('cleaners')
        artifactory.security().createOrUpdateGroup(group.build())
        def user = artifactory.security().builders().userBuilder().name('nobody')
        user.email('nobody@foo.bar').password('password').admin(false).groups(['cleaners'])
        artifactory.security().createOrUpdate(user.build())
        def princ = artifactory.security().builders().principalBuilder().name('nobody')
        princ.privileges(Privilege.DELETE, Privilege.DEPLOY, Privilege.READ)
        def princs = artifactory.security().builders().principalsBuilder().users(princ.build())
        def perm = artifactory.security().builders().permissionTargetBuilder().name('testperm')
        perm.repositories('maven-local').includesPattern('**/ban/**').principals(princs.build())
        artifactory.security().createOrReplacePermissionTarget(perm.build())

        when:        
        ArtifactoryClientBuilder.create().setUrl('http://localhost:8088/artifactory')
            .setUsername('nobody').setPassword('password').build().
            plugins().execute('cleanup').
            withParameter('repos', 'maven-local').
            withParameter('timeUnit', 'month').
            withParameter('timeInterval', '0').sync()
        artifactory.repository('maven-local').file('foo/bar/test').info()

        then:
        notThrown(HttpResponseException)

        when:
        artifactory.repository('maven-local').file('foo/ban/test').info()

        then:
        thrown(HttpResponseException)

        cleanup:
        artifactory.repository('maven-local').delete()
        artifactory.security().deletePermissionTarget('testperm')
        artifactory.security().deleteUser('nobody')
        artifactory.security().deleteGroup('cleaners')
    }

    def 'artifact cleanup test with control features'() {
        setup:
        def artifactory = createClientAndMavenRepo('maven-local')

        def file1 = new ByteArrayInputStream('test1'.bytes)
        artifactory.repository('maven-local').upload('test1', file1).doUpload()
        def file2 = new ByteArrayInputStream('test2'.bytes)
        artifactory.repository('maven-local').upload('test2', file2).doUpload()

        when:
        Thread.start {
            artifactory.plugins().execute('cleanup').
                withParameter('repos', 'maven-local').
                withParameter('timeUnit', 'month').
                withParameter('timeInterval', '0').
                withParameter('paceTimeMS', '5000').sync()
        }
        Thread.sleep(2500)
        artifactory.plugins().execute('cleanupCtl').
            withParameter('command', 'stop').sync()
        Thread.sleep(3000)
        artifactory.repository('maven-local').file('test1').info()

        then:
        thrown(HttpResponseException)

        when:
        artifactory.repository('maven-local').file('test2').info()

        then:
        notThrown(HttpResponseException)

        cleanup:
        artifactory.repository('maven-local').delete()
    }

    def 'artifact cleanup test with cron job'() {
        setup:
        def path1 = "/var/opt/jfrog/artifactory/etc/artifactory/plugins/artifactCleanup.json"
        def path2 = "/var/opt/jfrog/artifactory/etc/plugins/artifactCleanup.json"
        def plugin1 = "/var/opt/jfrog/artifactory/etc/artifactory/plugins/artifactCleanup.groovy"
        def plugin2 = "/var/opt/jfrog/artifactory/etc/plugins/artifactCleanup.groovy"
        def innercontent = [
            cron: "0/10 * * * * ?",
            repos: ["maven-local"],
            timeUnit: "month",
            timeInterval: 0,
            dryRun: false,
            paceTimeMS: 500,
            disablePropertiesSupport: true
        ]
        def content = [policies: [innercontent]]
        try {
            Control.setFileContent(8088, path1, new JsonBuilder(content).toString())
            Control.touchFile(8088, plugin1)
        } catch (Exception ex) {
            Control.setFileContent(8088, path2, new JsonBuilder(content).toString())
            Control.touchFile(8088, plugin2)
        }
        def artifactory = createClientAndMavenRepo('maven-local')
        def reloadreq = new ArtifactoryRequestImpl()
            .apiUrl("api/plugins/reload")
            .method(ArtifactoryRequest.Method.POST)
        artifactory.restCall(reloadreq)

        def file = new ByteArrayInputStream('test'.bytes)
        artifactory.repository('maven-local').upload('test', file).doUpload()

        when:
        Thread.sleep(12000)
        artifactory.repository('maven-local').file('test').info()

        then:
        thrown(HttpResponseException)

        cleanup:
        artifactory.repository('maven-local').delete()
    }
}
