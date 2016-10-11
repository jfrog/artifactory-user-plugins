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

def remoteCall(baseurl, auth, method, textFile){
  switch(method) {
    case 'ping':
      def url = "${baseurl}/api/system/ping"
      req = new HttpGet(url)
      req.addHeader("User-Agent", HttpUtils.artifactoryUserAgent)
      req.addHeader("Authorization", auth)
      break
    case 'post':
      def url = "${baseurl}/api/plugins/execute/securityReplication"
      req = new HttpPut(url)
      req.addHeader("User-Agent", HttpUtils.artifactoryUserAgent)
      req.addHeader("Content-Type", "text/plain")
      req.addHeader("Authorization", auth)
      if (textFile.text){ 
        req.entity = new StringEntity(textFile.text)
      }
      break
    default: 
      throw new RuntimeException("Invalid method ${method}.")
  }
  HttpResponse resp = getHttpClient().execute(req)
  return resp.getStatusLine().getStatusCode()
}

def pushReplicationList(targetFile, auth) {
  targetFile.each{
    //check the health status of each artifactory instance
    def statusCode = remoteCall(it, auth, 'ping', null)
    if (statusCode != 200) {
      throw new RuntimeException("Health Check Failed: ${it}: HTTP error code: " + statusCode)
    }

    //push new file to all instances 
    statusCode = remoteCall(it, auth, 'post', targetFile)
    if (statusCode != 200) {
      throw new RuntimeException("File Push Failed: ${it}: HTTP error code: " + statusCode)
    }
  }
}

executions {
  def artHome = ctx.artifactoryHome.haAwareEtcDir

  // usage: curl -X POST http://localhost:8081/artifactory/api/plugins/execute/distSecRep
  distSecRep(httpMethod: 'POST') {
    def auth = org.artifactory.request.RequestThreadLocal.context.get().requestThreadLocal.request.getHeader("authorization")
    def targetFile = new File(artHome, '/plugins/securityReplication.txt')

    if (!targetFile || !targetFile.exists()) {
      message = "Error: no securityReplication.txt file found"
      status = 400
    } else {
      pushReplicationList(targetFile, auth)
    }
  }

  // usage: curl -X PUT http://localhost:8081/artifactory/api/plugins/execute/securityReplication -T <textfile>
  securityReplication(httpMethod: 'PUT') { ResourceStreamHandle body ->
    def content = body.inputStream.text
    def targetFile = new File(artHome, '/plugins/securityReplication.txt')
    def w = targetFile.newWriter()
    targetFile << content
    w.close()
  }
  
  // usage: curl -X GET http://localhost:8081/artifactory/api/plugins/execute/securityReplicationList
  securityReplicationList(httpMethod: 'GET') {
    def content = new File(artHome, '/plugins/securityReplication.txt')
    if (!content || !content.exists()){
      message = "Error the security replication file either does not exist or contains no contents"
      status = 400
    } else {
      message = content.text
      status = 200
    }
  } 

  // usage: curl -X POST http://localhost:8081/artifactory/api/plugins/execute/securityReplicationData?params=action=<action> -d <hash/data>
  securityReplicationData(version: '1.0') { params, ResourceStreamHandle body ->
    def action = params?.('action')?.get(0) as String

    switch (action) {
      case 'hash':
        if (GLOBAL_LOCK == true) {
          message = "Artifactory Instance is already locked"
          status = 400
          break
        } else {
          GLOBAL_LOCK = true
        }

        try {
          def hash = body.inputStream.text
        } catch (e) {
          GLOBAL_LOCK = false
          throw new RuntimeException("ERROR: " + e)
        }

        break
      case 'data':
        if (GLOBAL_LOCK == true) {
          message = "Artifactory Instance is already locked"
          status = 400
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
