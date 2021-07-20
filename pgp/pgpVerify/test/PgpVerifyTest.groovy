import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import spock.lang.Specification
import org.jfrog.artifactory.client.model.repository.settings.impl.MavenRepositorySettingsImpl
import org.jfrog.artifactory.client.ArtifactoryClientBuilder
import org.apache.http.client.HttpResponseException
    
class PgpVerifyTest extends Specification {

    static final def mockServerPort = 8000

    def 'pgp verify test'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = ArtifactoryClientBuilder.create().setUrl(baseurl)
            .setUsername('admin').setPassword('password').build()

        def builder = artifactory.repositories().builders()
        def local = builder.localRepositoryBuilder().key('maven-local')
        .repositorySettings(new MavenRepositorySettingsImpl()).build()
        artifactory.repositories().create(0, local)

        def xmlfile = new File('./src/test/groovy/PgpVerifyTest/maven-metadata.xml')
        def ascfile = new File('./src/test/groovy/PgpVerifyTest/maven-metadata.xml.asc')
        def badfile = new File('./src/test/groovy/PgpVerifyTest/bad-maven-metadata.xml.asc')

        artifactory.repository('maven-local').upload('foo/maven-metadata.xml', xmlfile).doUpload()
        artifactory.repository('maven-local').upload('foo/maven-metadata.xml.asc', ascfile).doUpload()
        artifactory.repository('maven-local').upload('bar/maven-metadata.xml', xmlfile).doUpload()
        artifactory.repository('maven-local').upload('bar/maven-metadata.xml.asc', badfile).doUpload()

        def mockServer = new PGPPublicKeyMockServer(port: mockServerPort)
        mockServer.start()

        when:
        artifactory.repository('maven-local').download('foo/maven-metadata.xml').doDownload()

        then:
        notThrown(HttpResponseException)

        when:
        artifactory.repository('maven-local').download('bar/maven-metadata.xml').doDownload()

        then:
        thrown(HttpResponseException)

        cleanup:
        mockServer.stop()
        artifactory.repository('maven-local').delete()
    }

    /**
     * PGP Public Key Mock Server
     */
    class PGPPublicKeyMockServer {

        HttpServer server
        int port = 8000

        def start() {
            InetSocketAddress address = new InetSocketAddress(port);
            server = HttpServer.create(address, 0);

            HttpHandler slackRequestValidatorHandler = new HttpHandler() {
                @Override
                void handle(HttpExchange exchange) throws IOException {
                    println "Received a new request"
                    def content = exchange.requestBody.text
                    println "Message content: $content"
                    def responseFile = new File('./src/test/groovy/PgpVerifyTest/public_key.asc')
                    exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, responseFile.length())
                    OutputStream os = exchange.getResponseBody()
                    os.write(responseFile.bytes)
                    os.close()
                    exchange.close()
                }
            }

            server.createContext("/", slackRequestValidatorHandler)
            server.start()
            println "Mock server started"
        }

        def stop() {
            server?.stop(0)
            println "Mock server stopped"
        }
    }

}
