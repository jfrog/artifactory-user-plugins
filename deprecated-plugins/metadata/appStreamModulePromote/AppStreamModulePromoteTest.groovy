import groovy.json.JsonSlurper
import spock.lang.Specification
import org.jfrog.artifactory.client.ArtifactoryClientBuilder
import org.jfrog.artifactory.client.ArtifactoryRequest.Method
import org.jfrog.artifactory.client.impl.ArtifactoryRequestImpl
import org.jfrog.artifactory.client.model.repository.settings.impl.YumRepositorySettingsImpl

class AppStreamModulePromoteTest extends Specification {
    def customModule = '''
    document: modulemd
    version: 2
    data:
      name: foo
      stream: 1.0
      version: 8010020191119214651
      context: eb48df33
      arch: x86_64
      summary: test module
      description: >-
        test module
      license:
        module:
        - MIT
        content:
        - GPLv3+
      artifacts:
        rpms:
        - foo-0:1.0-0.noarch'''

    def 'module promote test'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = ArtifactoryClientBuilder.create().setUrl(baseurl)
            .setUsername('admin').setPassword('password').build()
        def builder = artifactory.repositories().builders()
        def settings = new YumRepositorySettingsImpl()
        settings.yumRootDepth = 1
        def local = builder.localRepositoryBuilder().key('rpm-local')
            .repositorySettings(settings).build()
        artifactory.repositories().create(0, local)
        artifactory.repository('rpm-local').folder('a').create()
        artifactory.repository('rpm-local').folder('b').create()
        def remote = builder.remoteRepositoryBuilder().key('rpm-remote')
            .repositorySettings(new YumRepositorySettingsImpl())
            .url('http://mirror.centos.org/centos/').build()
        artifactory.repositories().create(0, remote)

        // create with missing rpms (fail)
        when:
        def resp = restCall(artifactory, Method.POST, 'createModule', [
            'target': 'rpm-local/a'
        ], customModule)

        then:
        resp.statusLine.statusCode == 400

        // create with missing rpms and force (module: a/foo:1.0)
        when:
        resp = restCall(artifactory, Method.POST, 'createModule', [
            'target': 'rpm-local/a',
            'force': 'true'
        ], customModule)

        then:
        resp.statusLine.statusCode == 200

        // promote copy from remote (fail)
        when:
        resp = restCall(artifactory, Method.POST, 'promoteModule', [
            'source': 'rpm-remote/8/AppStream/x86_64/os',
            'target': 'rpm-local/a',
            'name': 'ant',
            'version': '1.10'
        ])

        then:
        resp.statusLine.statusCode == 404

        // promote copy from remote and downloadMissing (module: a/ant:1.10)
        when:
        resp = restCall(artifactory, Method.POST, 'promoteModule', [
            'source': 'rpm-remote/8/AppStream/x86_64/os',
            'target': 'rpm-local/a',
            'name': 'ant',
            'version': '1.10',
            'downloadMissing': 'true'
        ])

        then:
        resp.statusLine.statusCode == 200

        // promote copy missing module (fail)
        when:
        resp = restCall(artifactory, Method.POST, 'promoteModule', [
            'source': 'rpm-remote/8/AppStream/x86_64/os',
            'target': 'rpm-local/a',
            'name': 'cant',
            'version': '1.10',
            'downloadMissing': 'true'
        ])

        then:
        resp.statusLine.statusCode == 404

        // promote move (module: b/ant:1.10)
        when:
        resp = restCall(artifactory, Method.POST, 'promoteModule', [
            'source': 'rpm-local/a',
            'target': 'rpm-local/b',
            'name': 'ant',
            'version': '1.10',
            'move': 'true'
        ])

        then:
        resp.statusLine.statusCode == 200

        // verify a/foo:1.0 (fail)
        when:
        resp = restCall(artifactory, Method.GET, 'verifyModule', [
            'target': 'rpm-local/a',
            'name': 'foo',
            'version': '1.0'
        ])

        then:
        resp.statusLine.statusCode == 200
        new JsonSlurper().parseText(resp.rawBody).missing.size() == 1

        // verify a/ant:1.10 (fail)
        when:
        resp = restCall(artifactory, Method.GET, 'verifyModule', [
            'target': 'rpm-local/a',
            'name': 'ant',
            'version': '1.10'
        ])

        then:
        resp.statusLine.statusCode == 404

        // verify b/ant:1.10
        when:
        resp = restCall(artifactory, Method.GET, 'verifyModule', [
            'target': 'rpm-local/b',
            'name': 'ant',
            'version': '1.10'
        ])

        then:
        resp.statusLine.statusCode == 200
        new JsonSlurper().parseText(resp.rawBody).missing.size() == 0

        // delete b/ant:1.10
        when:
        resp = restCall(artifactory, Method.POST, 'deleteModule', [
            'target': 'rpm-local/b',
            'name': 'ant',
            'version': '1.10'
        ])

        then:
        resp.statusLine.statusCode == 200

        // view a/foo:1.0
        when:
        resp = restCall(artifactory, Method.GET, 'viewModule', [
            'target': 'rpm-local/a',
            'name': 'foo',
            'version': '1.0'
        ])

        then:
        resp.statusLine.statusCode == 200

        // view a/ant:1.10 (fail)
        when:
        resp = restCall(artifactory, Method.GET, 'viewModule', [
            'target': 'rpm-local/a',
            'name': 'ant',
            'version': '1.10'
        ])

        then:
        resp.statusLine.statusCode == 404

        // view b/ant:1.10 (fail)
        when:
        resp = restCall(artifactory, Method.GET, 'viewModule', [
            'target': 'rpm-local/b',
            'name': 'ant',
            'version': '1.10'
        ])

        then:
        resp.statusLine.statusCode == 404

        cleanup:
        artifactory.repository("rpm-local").delete()
        artifactory.repository("rpm-remote").delete()
    }

    private def restCall(artifactory, method, endpoint, params, body=null) {
        def path = "api/plugins/execute/$endpoint?params="
        path += params.collect({ k, v -> "$k=$v" }).join(";")
        def req = new ArtifactoryRequestImpl().method(method).apiUrl(path)
        if (body) {
            req.requestBody(body)
        }
        return artifactory.restCall(req)
    }
}
