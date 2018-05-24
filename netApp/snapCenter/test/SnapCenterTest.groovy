import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import spock.lang.Specification

import org.jfrog.artifactory.client.ArtifactoryClientBuilder

class SnapCenterTest extends Specification {
    def baseurl = 'http://localhost:8088/artifactory'
    def artifactory = ArtifactoryClientBuilder.create().setUrl(baseurl)
        .setUsername('admin').setPassword('password').build()
    def auth = "Basic ${("admin:password").bytes.encodeBase64().toString()}"
    final def snapCenterMockServerPort = 8146
    final def snapCenterMockServerUrl = "http://localhost:$snapCenterMockServerPort/"

    def 'Snap Center test'() {
        setup:
        def snapCenterMockServer = new snapCenterMockServer(port: snapCenterMockServerPort)
        snapCenterMockServer.start()
        sleep(5000)

        when:
        def conn = new URL("$baseurl/api/plugins/execute/snapCenter").openConnection()
        conn.requestMethod = 'POST'
        conn.doOutput = true
        conn.setRequestProperty('Authorization', auth)
        conn.setRequestProperty('Content-Type', 'application/json')
        def file = new File("./src/test/groovy/SnapCenterTest/body.json")
        conn.outputStream << file.text

        then:
        assert conn.responseCode == 200
        // A message must have been received
        snapCenterMockServer.messageReceived == true
        // And it must be a valid one
        snapCenterMockServer.validMessageReceived == true

        cleanup:
        snapCenterMockServer?.stop()
        conn.disconnect()
    }
}

class snapCenterMockServer {

    HttpServer server
    int port = 8146
    boolean messageReceived
    boolean validMessageReceived
    String requiredMessage

    def start() {
        InetSocketAddress address = new InetSocketAddress(port);
        server = HttpServer.create(address, 0);

        HttpHandler snapCenterRequestValidatorHandler = new HttpHandler() {
            @Override
            void handle(HttpExchange exchange) throws IOException {

                println "Snap Center mock server receive a new request"
                messageReceived = true
                def content = exchange.requestBody.text
                def responseText = "{\"Backup\":null,\"Context\":null,\"DisplayCount\":0,\"Job\":{\"ApisJobKey\":null,\"Description\":null,\"EndTime\":null,\"Error\":null,\"EventId\":0,\"Id\":1397,\"IsCancellable\":false,\"IsCompleted\":false,\"IsRestartable\":false,\"IsScheduled\":false,\"IsVisible\":true,\"JobTypeId\":0,\"Name\":\"Backup of ResourceGroup 'artdb 'with policy 'ART_Policy' \",\"ObjectId\":0,\"Owner\":null,\"ParentJobID\":0,\"PercentageCompleted\":0,\"PluginCode\":0,\"PluginName\":null,\"Priority\":0,\"StartTime\":\"2017-09-18T09: 57: 27.3345995-07: 00\",\"Status\":5,\"Tasks\":{}},\"Result\":{\"ErrorRecords\":{},\"_errorCode\":0,\"_message\":\" \"},\"Results\":{},\"TotalCount\":0}"
                def response = new JsonBuilder(new JsonSlurper().parseText(responseText)).toString().bytes
                println "Message content: $content"

                validMessageReceived = isMessageContentValid(content)
                exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, response.length)
                exchange.getResponseHeaders().set("Content-Type", "application/json")
                OutputStream os = exchange.getResponseBody()
                os.write(response)
                os.close()
                exchange.close()
            }

            /**
             * Check if message content is valid
             * @param content
             * @return
             */
            boolean isMessageContentValid(content) {
                def jsonContent = new JsonSlurper().parseText(content)

                assert jsonContent.Policy != null
                assert jsonContent.Policy.Name != null
                if (requiredMessage != null) {
                    assert jsonContent.text.contains(requiredMessage)
                }

                return true
            }
        }

        server.createContext("/api/2.0/resourcegroups/db1_netapp_local_MySQL_artdb/backup", snapCenterRequestValidatorHandler)
        server.start()
        println "Snap Center mock server started"
    }

    def stop() {
        server?.stop(0)
        println "Snap Center mock server stopped"
    }
}
