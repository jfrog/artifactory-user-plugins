import groovyx.net.http.HttpResponseException
import spock.lang.Specification

class PluginsConfigTest extends Specification {
  def 'list installed plugins test'() {
    setup:
    def baseurl = 'http://localhost:8088/artifactory/api/plugins/execute'
    def auth = "Basic ${'admin:password'.bytes.encodeBase64()}"
    def conn = null

    when:
    conn = new URL("${baseurl}/listPlugins").openConnection()
    conn.requestMethod = 'GET'
    conn.setRequestProperty('Authorization', auth)
    assert conn.responseCode == 200
    conn.disconnect()

    then:
    notThrown(HttpResponseException)
  }

  def 'download plugin test'(){
    setup:
    def baseurl = 'http://localhost:8088/artifactory/api/plugins/execute'
    def params = '?params=name=pluginsConfig.groovy'
    def auth = "Basic ${'admin:password'.bytes.encodeBase64()}"
    def conn = null

    when:
    conn = new URL("${baseurl}/downloadPlugin${params}").openConnection()
    conn.requestMethod = 'GET'
    conn.setRequestProperty('Authorization', auth)
    assert conn.responseCode == 200
    conn.disconnect()

    then:
    notThrown(HttpResponseException)
  }
}
