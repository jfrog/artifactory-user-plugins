/*
 * Copyright (C) 2016 JFrog Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.apache.http.client.methods.*
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.HttpClients
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;

import org.artifactory.util.HttpClientConfigurator
import org.artifactory.util.HttpUtils

import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.transaction.support.TransactionSynchronizationManager

import org.artifactory.resource.ResourceStreamHandle
import static org.artifactory.repo.RepoPathFactory.create

import groovy.json.*

httpclient = null
GLOBAL_LOCK = null

def getHttpClient() {
  if (!httpclient) {
    def builder = HttpClients.custom()
    builder.maxConnTotal = 50
    builder.maxConnPerRoute = 25
    httpclient = builder.build()
  }
  return httpclient
}

def remoteCall(baseurl, auth, method, textFile, action){
  switch(method) {
    case 'ping':
      def url = "${baseurl}/api/system/ping"
      req = new HttpGet(url)
      req.addHeader("User-Agent", HttpUtils.artifactoryUserAgent)
      req.addHeader("Authorization", auth)
      break
    case 'put':
      def url = "${baseurl}/api/plugins/execute/securityReplication"
      req = new HttpPut(url)
      req.addHeader("User-Agent", HttpUtils.artifactoryUserAgent)
      req.addHeader("Content-Type", "text/plain")
      req.addHeader("Authorization", auth)
      if (textFile){ 
        req.entity = new StringEntity(textFile)
      }
      break
    case 'data':
      def url = "${baseurl}/api/plugins/execute/secRepData?params=action=${action}"
      req = new HttpPost(url)
      req.addHeader("User-Agent", HttpUtils.artifactoryUserAgent)
      req.addHeader("Content-Type", "application/json")
      req.addHeader("Authorization", auth)
      if (textFile){
        req.entity = new StringEntity(textFile)
      }
      println url
      break
    default: 
      throw new RuntimeException("Invalid method ${method}.")
  }
  HttpResponse resp = getHttpClient().execute(req)
  return resp.getStatusLine().getStatusCode()
}

def pushData(targetFile, auth) {
  def artHome = ctx.artifactoryHome.haAwareEtcDir
  
  def slurped = null
  try {
    slurped = new JsonSlurper().parseText(targetFile.text)
  } catch (groovy.json.JsonException ex) {
    message = "Problem parsing JSON: ${ex}.message"
    status = 400
    return
  }

  def distList = slurped.securityReplication.urls
  distList.each{
    if (slurped.securityReplication.whoami != it) {
      //check the health status of each artifactory instance
      def statusCode = remoteCall(it, auth, 'ping', null, null)
      if (statusCode != 200) {
        throw new RuntimeException("Health Check Failed: ${it}: HTTP error code: " + statusCode)
      }

      slurped.securityReplication.whoami = "${it}"
      def builder = new JsonBuilder(slurped)

      //push new file to all instances 
      statusCode = remoteCall(it, auth, 'put', builder.toString(), null)
      if (statusCode != 200) {
        throw new RuntimeException("File Push Failed: ${it}: HTTP error code: " + statusCode)
      }
    }
  }
}

executions {
  def artHome = ctx.artifactoryHome.haAwareEtcDir

  // usage: curl -X POST http://localhost:8081/artifactory/api/plugins/execute/distSecRep
  distSecRep(httpMethod: 'POST') {
    def auth = org.artifactory.request.RequestThreadLocal.context.get().requestThreadLocal.request.getHeader("authorization")
    def targetFile = new File(artHome, '/plugins/securityReplication.json')
    if (!targetFile || !targetFile.exists()) {
      message = "Error: no securityReplication.json file found"
      status = 400
    } else {
      pushData(targetFile, auth)
    }
  }

  // usage: curl -X PUT http://localhost:8081/artifactory/api/plugins/execute/securityReplication -T <textfile>
  securityReplication(httpMethod: 'PUT') { ResourceStreamHandle body ->
    def reader = new InputStreamReader(body.inputStream, 'UTF-8')
    def slurped = null
    try {
      slurped = new JsonSlurper().parse(reader)
    } catch (groovy.json.JsonException ex) {
      message = "Problem parsing JSON: ${ex}.message"
      status = 400
      return
    }
    def builder = new JsonBuilder (slurped)
    def targetFile = new File(artHome, '/plugins/securityReplication.json')
    def w = targetFile.newWriter()
    targetFile << builder
    w.close()
  }

  // usage: curl -X GET http://localhost:8081/artifactory/api/plugins/execute/secRepList
  secRepList(httpMethod: 'GET') {
    def inputFile = new File(artHome, '/plugins/securityReplication.json')
    try {
      slurped = new JsonSlurper().parseText(inputFile.text)
    } catch (groovy.json.JsonException ex) {
      message = "Problem parsing JSON: ${ex}.message"
      status = 400
      return
    }

    if (!inputFile || !inputFile.exists()){
      message = "Error the security replication file either does not exist or contains no contents"
      status = 400
    } else {
      message = slurped.securityReplication.urls
      status = 200
    }
  }

  // usage: curl -X POST http://localhost:8081/artifactory/api/plugins/execute/secRepData?params=action=<action> -d <hash/data>
  secRepData(httpMethod: 'POST') { params, ResourceStreamHandle body ->
    def action = params?.('action')?.get(0) as String

    switch (action) {
      case 'hash':
        if (GLOBAL_LOCK == true) {
          log.warn("HASH: Artifactory Instance is already locked")
          message = "HASH: Artifactory Instance is already locked"
          status = 400
          break
        } else {
          log.info("Locking Artifactory Instance")
          GLOBAL_LOCK = true
          message = "Locking Artifactory Instance"
          status = 200
        }

        try {
          def hash = body.inputStream.text
          println hash
        } catch (e) {
          GLOBAL_LOCK = false
          throw new RuntimeException("ERROR: " + e)
        }

        break
      case 'data':
        if (GLOBAL_LOCK == true) {
          log.warn("DATA: Artifactory Instance is already locked")
          break
        }
 
        try {
          def data = body.inputStream.text
        } catch (e) {
          GLOBAL_LOCK = false
          throw new RuntimeException("Error: " + e)
        }

        GLOBAL_LOCK = false
        break
      default: throw new RuntimeException("Invalide action ${action}")
    }
  }
}
jobs {
  lockAndHash(cron: "0 */1 * * * ?") {
    def artHome = ctx.artifactoryHome.haAwareEtcDir
    def targetFile = new File(artHome, "/plugins/securityReplication.json")
    def testFile = new File(artHome, "/plugins/testFile.json")
    try {
      slurped = new JsonSlurper().parseText(targetFile.text)
      slurped2 = new JsonSlurper().parseText(testFile.text)
    } catch (groovy.json.JsonException ex) {
      log.error("Problem parsing JSON: ${ex}.message")
      return
    }

    def builder = new JsonBuilder(slurped2)
    def distList = slurped.securityReplication.urls
    def username = slurped.securityReplication.authorization.username
    def password = slurped.securityReplication.authorization.password
    def encoded = "$username:$password".getBytes().encodeBase64().toString()
    def auth = "Basic ${encoded}"    

    distList.each {
      def statusCode = remoteCall(it, auth, 'ping', null, null)
      if (statusCode != 200) {
        log.error("Error: Artifactory Instance URL: ${it} is not up")
      } else {
        log.info("${it} is up")
        statusCode = remoteCall(it, auth, 'data', builder.toString(), 'hash')
        if (statusCode != 200) {
          log.error("Error: Artifactory Instance URL: ${it} is not accepting commands: ${statusCode}")
        } else {
          log.info("${it} send succeeded")
        }
      }
    }
  }
}
