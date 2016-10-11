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

import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import java.security.MessageDigest

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

// TODO see if this can be a normal function in the plugin
diffsort = { left, right ->
    def ls = left[0].size(), rs = right[0].size()
    for (i in 0..([ls, rs].min())) {
        if (left[0][i] != right[0][i]) return left[0][i] <=> right[0][i]
    }
    if (ls != rs) return ls <=> rs
    return right[1] <=> left[1]
}

def diff(path, ptch, oldver, newver) {
    if ((oldver == null && newver == null)
        || (oldver instanceof CharSequence && newver instanceof CharSequence)
        || (oldver instanceof Boolean && newver instanceof Boolean)
        || (oldver instanceof Number && newver instanceof Number)) {
        if (oldver != newver) ptch << [path, ':~', newver]
    } else if (oldver instanceof Map && newver instanceof Map) {
        for (k in oldver.keySet() + newver.keySet()) {
            def oldkey = oldver.containsKey(k), newkey = newver.containsKey(k)
            if (oldkey && newkey) diff(path + k, ptch, oldver[k], newver[k])
            else if (!oldkey && newkey) ptch << [path, ';+', k, newver[k]]
            else if (oldkey && !newkey) ptch << [path, ';-', k]
        }
    } else if (oldver instanceof Iterable && newver instanceof Iterable) {
        for (v in (oldver as Set) + (newver as Set)) {
            def oldent = oldver.contains(v), newent = newver.contains(v)
            if (!oldent && newent) ptch << [path, ':+', v]
            else if (oldent && !newent) ptch << [path, ':-', v]
        }
    } else throw new RuntimeException("Bad JSON values '$oldver', '$newver'")
}

def patch(path, ptch, oldver) {
    if (oldver == null || oldver instanceof CharSequence
        || oldver instanceof Boolean || oldver instanceof Number) {
        def newver = oldver
        while (ptch && ptch[0][0] == path) {
            if (ptch[0][1] == ':~') newver = ptch[0][2]
            else throw new RuntimeException("Bad patch ${ptch[0]} for $oldver")
            ptch.remove(0)
        }
        return newver
    } else if (oldver instanceof Map) {
        def ins = [:], del = [] as Set
        while (ptch && ptch[0][0] == path) {
            if (ptch[0][1] == ';+') ins[ptch[0][2]] = ptch[0][3]
            else if (ptch[0][1] == ';-') del << ptch[0][2]
            else throw new RuntimeException("Bad patch ${ptch[0]} for $oldver")
            ptch.remove(0)
        }
        def mappatch = { k, v -> [k, patch(path + k, ptch, v)] }
        def newver = oldver.collectEntries(mappatch)
        newver += ins
        del.each { newver.remove(it) }
        return newver
    } else if (oldver instanceof Iterable) {
        def ins = [], del = []
        while (ptch && ptch[0][0] == path) {
            if (ptch[0][1] == ':+') ins << ptch[0][2]
            else if (ptch[0][1] == ':-') del << ptch[0][2]
            else throw new RuntimeException("Bad patch ${ptch[0]} for $oldver")
            ptch.remove(0)
        }
        if (!ins && !del) return oldver
        return oldver + ins - del
    } else throw new RuntimeException("Bad JSON value '$oldver'")
}

def normalize(json) {
    if (json == null || json instanceof CharSequence
        || json instanceof Boolean || json instanceof Number) {
        return json
    } else if (json instanceof Map) {
        return json.collectEntries({ k, v -> [k, normalize(v)] }).sort()
    } else if (json instanceof Iterable) {
        return json.collect({ normalize(it) }).sort()
    } else throw new RuntimeException("Bad JSON value '$json'")
}

def merge(patches) {
    def result = []
    patches.each { result.addAll(it) }
    return result.unique({ [it[0], it[1]] }).sort(diffsort).unique { l, r ->
        if (l[1] != ';-' && r[1] != ';-') return 1
        if (l[1] == r[1] && l[0].size() > r[0].size()) (l, r) = [r, l]
        if (l[1] != ';-') (l, r) = [r, l]
        if (l[0].size() > r[0].size()) return 1
        if (l[0] != r[0][0..<l[0].size()]) return 1
        return 0
    }
}

def builddiff(oldver, newver) {
    def ptch = []
    diff([], ptch, normalize(oldver), normalize(newver))
    return ptch
}

def applydiff(oldver, ptch) {
    return patch([], ptch.sort(diffsort), normalize(oldver))
}

def gethash(json) {
    def digest = MessageDigest.getInstance("SHA-1")
    def hash = digest.digest(new JsonBuilder(normalize(json)).toString().bytes)
    return hash.encodeHex().toString()
}
