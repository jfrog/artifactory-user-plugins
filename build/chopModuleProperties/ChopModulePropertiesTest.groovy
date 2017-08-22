import spock.lang.Specification

import static org.jfrog.artifactory.client.ArtifactoryClient.create
import org.apache.http.client.HttpResponseException
import org.jfrog.artifactory.client.ArtifactoryRequest
import org.jfrog.artifactory.client.impl.ArtifactoryRequestImpl

import groovy.json.JsonSlurper

class ChopModulePropertiesTest extends Specification {
 def 'ChopModulePropertiesTest name' () {

  setup: def baseurl = 'http://localhost:8088/artifactory'
  def artifactory = create(baseurl, 'admin', 'password')

  def file = new File("./src/test/groovy/build.json")
  def request = new JsonSlurper().parse(file)

  ArtifactoryRequest uploadBuild = new ArtifactoryRequestImpl().apiUrl("api/build")
  .method(ArtifactoryRequest.Method.PUT)
  .requestType(ArtifactoryRequest.ContentType.JSON)
  .requestBody(new JsonSlurper().parse(file))

  artifactory.restCall(uploadBuild)


  when: ArtifactoryRequest getBuildInfo = new ArtifactoryRequestImpl().apiUrl("api/build/test-build/1")
   .method(ArtifactoryRequest.Method.GET)
   .responseType(ArtifactoryRequest.ContentType.JSON)

   def response = artifactory.restCall(getBuildInfo)


   then:

   boolean isGood = true

   response.buildInfo.modules.each {
   m ->
    print "m.properties: ${m.properties}"
   Map < String, String > changed = [: ]
   m.properties.each {
    String k, String v ->
     if (v.length() > 899) {
      isGood = false

     }
   }
  }

   isGood


   cleanup:

   ArtifactoryRequest delete = new ArtifactoryRequestImpl().apiUrl("api/build/test-build")
   .setQueryParams(deleteAll: 1)
   .method(ArtifactoryRequest.Method.DELETE)
  artifactory.restCall(delete)

 }
}



