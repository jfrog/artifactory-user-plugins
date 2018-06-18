import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import org.apache.http.client.HttpResponseException
import spock.lang.Specification

import org.jfrog.artifactory.client.ArtifactoryClientBuilder
import org.jfrog.artifactory.client.model.builder.UserBuilder

class CleanExternalUsersTest extends Specification {

    static final def oktaMockServerPort = 8000

    def 'clean external users test'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = ArtifactoryClientBuilder.create().setUrl(baseurl)
            .setUsername('admin').setPassword('password').build()
        def oktaMockServer = new OktaMockServer(port: oktaMockServerPort)
        oktaMockServer.start()

        when:
        def userBuilder = artifactory.security().builders().userBuilder()
        def user = userBuilder.name("deleteme@foo.bar")
        user.email("deleteme@foo.bar").password("password")
        artifactory.security().createOrUpdate(user.build())
        artifactory.security().user("deleteme@foo.bar").email
        artifactory.plugins().execute("cleanExternalUsers").sync()

        then:
        notThrown(HttpResponseException)

        when:
        artifactory.security().user("deleteme@foo.bar").email

        then:
        thrown(HttpResponseException)

        cleanup:
        oktaMockServer.stop()
    }

    /**
     * Okta mock server
     *
     * This server is responsible for receiving and validating Okta requests
     */
    class OktaMockServer {

        HttpServer server
        int port = 8000

        def start() {
            InetSocketAddress address = new InetSocketAddress(port);
            server = HttpServer.create(address, 0);

            server.createContext("/", new HttpHandler() {
                @Override
                void handle(HttpExchange exchange) throws IOException {

                    try {
                        println "Okta mock server received a new request"
                        def content = exchange.requestBody.text
                        println "Message content: $content"

                        def users = [
                                [id: 1, credentials: [userName: 'donotdeleteme@foo.bar']]
                        ]

                        JsonBuilder responseBody = new JsonBuilder(users)
                        println "Response:\n${responseBody.toPrettyString()}"

                        def responseBytes = responseBody.toString().bytes

                        exchange.getResponseHeaders().set('Content-Type', 'application/json')
                        exchange.getResponseHeaders().set('Link', '<foobar>; rel=self')
                        exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, responseBytes.size())
                        exchange.getResponseBody().write(responseBytes)

                    } catch (Exception e) {
                        e.printStackTrace()
                    } finally {
                        exchange.sendResponseHeaders(HttpURLConnection.HTTP_INTERNAL_ERROR, 0)
                        exchange.close()
                    }
                }

            });

            server.start()
            println "Okta mock server started"
        }

        def stop() {
            server?.stop(0)
            println "Okta mock server stopped"
        }
    }
}
