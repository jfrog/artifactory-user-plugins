package SecurityReplicationTest

import spock.lang.Specification
import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import org.jfrog.artifactory.client.ArtifactoryClientBuilder
import org.jfrog.artifactory.client.impl.ArtifactoryRequestImpl
import org.jfrog.artifactory.client.ArtifactoryRequest

class PermissionReplication extends Specification {
    def 'replicate permissions while changing the filter level'() {
        setup:
        def pass1ct
        def pass2ct
        def pass3ct

        when: "create fifteen permissions on master and run replication"
        createPerms('8088', 'temptest-fifteen-', 15)
        doReplication('8088')
        pass1ct = countPerms('8088', 'temptest-')

        then: "ensure all nodes have the same permission count"
        pass1ct == countPerms('8090', 'temptest-')
        pass1ct == countPerms('8091', 'temptest-')
        pass1ct == countPerms('8092', 'temptest-')

        when: "change all configs to l2, create 100 permissions on master, run replication"
        setLevel('8088', 2)
        setLevel('8090', 2)
        setLevel('8091', 2)
        setLevel('8092', 2)
        createPerms('8088', 'temptest-master-hundred-', 100)
        createPerms('8090', 'temptest-slave-hundred-', 100)
        doReplication('8088')
        pass2ct = pass1ct + 100

        then: "ensure permissions did not replicate"
        pass2ct == countPerms('8088', 'temptest-')
        pass2ct == countPerms('8090', 'temptest-')
        pass1ct == countPerms('8091', 'temptest-')
        pass1ct == countPerms('8092', 'temptest-')

        when: "settle system by running an empty replication"
        doReplication('8088')

        then: "ensure that nothing changed"
        pass2ct == countPerms('8088', 'temptest-')
        pass2ct == countPerms('8090', 'temptest-')
        pass1ct == countPerms('8091', 'temptest-')
        pass1ct == countPerms('8092', 'temptest-')

        when: "change master-primary config to l3 and run replication"
        setLevel('8088', 3)
        doReplication('8088')
        pass3ct = pass1ct + 200

        then: "ensure all nodes have the new number of permissions"
        pass3ct == countPerms('8088', 'temptest-')
        pass3ct == countPerms('8090', 'temptest-')
        pass3ct == countPerms('8091', 'temptest-')
        pass3ct == countPerms('8092', 'temptest-')

        cleanup:
        deletePerms('8088', 'temptest-')
        deletePerms('8090', 'temptest-')
        deletePerms('8091', 'temptest-')
        deletePerms('8092', 'temptest-')
        setLevel('8088', 3)
        setLevel('8090', 3)
        setLevel('8091', 3)
        setLevel('8092', 3)
        doReplication('8088')
    }

    void createPerms(port, prefix, count) {
        def art = ArtifactoryClientBuilder.create()
            .setUrl("http://localhost:$port/artifactory/")
            .setUsername('admin').setPassword('password').build()
        for (i in 1..count) {
            def targ = art.security().builders().permissionTargetBuilder()
                .name("${prefix}${i}-priv")
                .repositories('example-repo-local').build()
            art.security().createOrReplacePermissionTarget(targ)
        }
    }

    void deletePerms(port, prefix) {
        def art = ArtifactoryClientBuilder.create()
            .setUrl("http://localhost:$port/artifactory/")
            .setUsername('admin').setPassword('password').build()
        for (targ in art.security().permissionTargets()) {
            if (targ.startsWith(prefix)) {
                art.security().deletePermissionTarget(targ)
            }
        }
    }

    int countPerms(port, prefix) {
        def count = 0
        def art = ArtifactoryClientBuilder.create()
            .setUrl("http://localhost:$port/artifactory/")
            .setUsername('admin').setPassword('password').build()
        for (targ in art.security().permissionTargets()) {
            if (targ.startsWith(prefix)) {
                count += 1
            }
        }
        return count
    }

    void setLevel(port, level) {
        def art = ArtifactoryClientBuilder.create()
            .setUrl("http://localhost:$port/artifactory/")
            .setUsername('admin').setPassword('password').build()
        def req1 = new ArtifactoryRequestImpl()
            .apiUrl('api/plugins/execute/secRepJson')
            .method(ArtifactoryRequest.Method.GET)
            .responseType(ArtifactoryRequest.ContentType.TEXT)
        def resp1 = art.restCall(req1).getRawBody()
        def json = new JsonSlurper().parseText(resp1)
        json.securityReplication.filter = level
        def req2 = new ArtifactoryRequestImpl()
            .apiUrl('api/plugins/execute/securityReplication')
            .method(ArtifactoryRequest.Method.PUT)
            .requestType(ArtifactoryRequest.ContentType.TEXT)
            .requestBody(new JsonBuilder(json).toString())
        def resp2 = art.restCall(req2).getRawBody()
    }

    void doReplication(port) {
        def art = ArtifactoryClientBuilder.create()
            .setUrl("http://localhost:$port/artifactory/")
            .setUsername('admin').setPassword('password').build()
        art.plugins().execute('testRunSecurityReplication').sync()
    }
}
