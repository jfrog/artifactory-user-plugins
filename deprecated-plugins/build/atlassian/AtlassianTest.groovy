import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import spock.lang.Specification
import org.jfrog.artifactory.client.ArtifactoryClientBuilder

class AtlassianTest extends Specification {
    def 'atlassian plugin test'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def auth = "Basic ${("admin:password").bytes.encodeBase64().toString()}"
        def artifactory = ArtifactoryClientBuilder.create().setUrl(baseurl)
            .setUsername('admin').setPassword('password').build()
        def mockServer = new MockServer(port: 7990)
        mockServer.start()

        when:
        def conn = new URL(baseurl + '/api/build').openConnection()
        conn.setDoOutput(true)
        conn.setRequestMethod('PUT')
        conn.setRequestProperty('Content-Type', 'application/json')
        conn.setRequestProperty('Authorization', auth)
        def jsonPayload = new JsonBuilder([version: '1.0.1', name: 'atlassian-test-build', number: '1', type: 'GENERIC', agent: [name: "Jenkins", version: "1.0.0"], buildAgent: [name: "Maven", version: "1.0.0"], started: '2020-03-05T21:47:17.229+0000', durationMillis: 2292103, vcsUrl: "http://localhost:7990/foo/bar", vcsRevision: 'b2869957593381150d3d3fb854674396b69e51c7'])
        conn.outputStream.withCloseable { output ->
            output.write(jsonPayload.toString().bytes)
        }
        assert conn.responseCode == 204
        mockServer.waitForMessage()

        then:
        mockServer.messageReceived == true
        mockServer.validMessageReceived == true

        cleanup:
        mockServer?.stop()
    }

    class MockServer {
        HttpServer server
        int port = 7990
        boolean messageReceived
        boolean validMessageReceived

        def start() {
            InetSocketAddress address = new InetSocketAddress(port);
            server = HttpServer.create(address, 0);
            HttpHandler requestValidatorHandler = new HttpHandler() {
                @Override
                void handle(HttpExchange exchange) throws IOException {
                    println "Mock server receive a new request"
                    messageReceived = true
                    def content = exchange.requestBody.text
                    println "Message content: $content"
                    validMessageReceived = isMessageContentValid(content)
                    exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, 0)
                    exchange.close()
                }

                boolean isMessageContentValid(content) {
                    def jsonContent = new JsonSlurper().parseText(content)
                    assert jsonContent.server == 'http://localhost:8088/artifactory'
                    assert jsonContent.state == 'SUCCESSFUL'
                    assert jsonContent.key == 'atlassian-test-build'
                    assert jsonContent.resultKey == 'atlassian-test-build:1'
                    assert jsonContent.name == 'atlassian-test-build'
                    assert jsonContent.url == "http://localhost:8088/ui/builds/atlassian-test-build/1/1583444837229"
                    assert jsonContent.description == "atlassian-test-build build 1 was successfully published to Artifactory at 2020-03-05T21:47:17.229+0000 by Jenkins/1.0.0 using tool Maven/1.0.0"
                    assert jsonContent.duration == 2292.103
                    return true
                }
            }
            server.createContext("/rest/build-status/1.0/commits/b2869957593381150d3d3fb854674396b69e51c7", requestValidatorHandler)
            server.start()
            println "Mock server started"
        }

        def stop() {
            server?.stop(0)
            println "Mock server stopped"
        }

        def waitForMessage() {
            println "Waiting up to 2 minutes for notification receiving..."
            def initialTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - initialTime < 120000) {
                sleep(5000)
                if (this.messageReceived) {
                    sleep (5000)
                    break
                }
            }
        }
    }
}
