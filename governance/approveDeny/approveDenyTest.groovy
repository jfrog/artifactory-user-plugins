import org.apache.http.client.HttpResponseException

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer

import groovy.json.JsonBuilder
import groovy.json.JsonSlurper

import spock.lang.Specification

import org.jfrog.artifactory.client.model.repository.settings.impl.DockerRepositorySettingsImpl
import org.jfrog.artifactory.client.ArtifactoryClientBuilder


class AcceptApproveTest extends Specification {
    def 'approve deny test'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = ArtifactoryClientBuilder.create().setUrl(baseurl)
            .setUsername('admin').setPassword('password').build()

        def builder = artifactory.repositories().builders()
        def local = builder.localRepositoryBuilder().key('docker-local')
            .repositorySettings(new DockerRepositorySettingsImpl()).build()
        artifactory.repositories().create(0, local)
        def mockServer = new MockServer(port: 8888)
        mockServer.start()

        when:
        def artifact = new ByteArrayInputStream("$status artifact".bytes)
        artifactory.repository('docker-local').upload(status, artifact).doUpload()
        artifactory.repository('docker-local').file(status).properties().addProperty('approver.status', status).doSet()
        mockServer.waitForMessage()

        then:
        testDownload(artifactory.repository('docker-local'), status, "$status artifact", approved)
        mockServer.messageReceived == true
        mockServer.validMessageReceived == true

        cleanup:
        artifactory.repository('docker-local').delete()
        mockServer?.stop()

        where:
        status     | approved
        'Approved' | true
        'DECLINE'  | false
    }

    def testDownload(repo, status, content, approved) {
        try {
            repo.download(status).doDownload().text == content && approved
        } catch (HttpResponseException ex) {
            !approved
        }
    }

    class MockServer {
        HttpServer server
        int port = 8888
        boolean messageReceived

        def start() {
            InetSocketAddress address = new InetSocketAddress(port);
            server = HttpServer.create(address, 0);
            HttpHandler requestValidatorHandler = new HttpHandler() {
                @Override
                void handle(HttpExchange exchange) throws IOException {
                    println "Mock server receive a new request"
                    String response = "{ \"status\": \"Approved\" }"
                    httpExchange.getResponseHeaders().set("Content-Type", "appication/json");
                    exchange.sendResponseHeaders(200, response.length());
                    OutputStream os = exchange.getResponseBody();
                    os.write(response.getBytes());
                    os.close();
                    exchange.close()
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
