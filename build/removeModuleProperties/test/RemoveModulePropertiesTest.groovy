import spock.lang.Specification
import static org.jfrog.artifactory.client.ArtifactoryClient.create
import groovy.json.*


class RemoveModulePropertiesTest extends Specification {
    def 'removeModulePropertiesTest'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = create(baseurl, 'admin', 'password')
        def slurper = new JsonSlurper()

        ['bash','-c','curl -uadmin:password -X PUT http://localhost:8088/artifactory/api/build -H "Content-Type: application/json" --data-binary @/Users/scottm/Documents/build.json'].execute()
        ['bash','-c','mkdir /tmp/buildScript'].execute()

        when:
        ['bash','-c','curl -uadmin:password -X GET http://localhost:8088/artifactory/api/build/stm-test/46 > /tmp/buildScript/output.json'].execute().waitFor()
        def buildFile = new File('/tmp/buildScript/output.json')

        then:
        def res = slurper.parseText(buildFile.getText())
        res.buildInfo.modules.each{ m ->
          m.properties == ':'
        }

        cleanup:
        ['bash','-c','rm -r /tmp/buildScript'].execute()
        ['bash','-c','curl -uadmin:password -X DELETE http://localhost:8088/artifactory/api/build/stm-test?deleteAll=1'].execute()
    }
}
