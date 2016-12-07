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

import com.google.common.collect.HashMultimap

import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import groovy.json.JsonException

import java.security.MessageDigest

import org.apache.http.client.methods.*
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients

import org.artifactory.model.xstream.security.AceImpl
import org.artifactory.model.xstream.security.AclImpl
import org.artifactory.model.xstream.security.GroupImpl
import org.artifactory.model.xstream.security.PermissionTargetImpl
import org.artifactory.model.xstream.security.UserImpl
import org.artifactory.request.RequestThreadLocal
import org.artifactory.resource.ResourceStreamHandle
import org.artifactory.security.SaltedPassword
import org.artifactory.storage.db.DbService
import org.artifactory.storage.db.util.DbUtils
import org.artifactory.storage.db.util.JdbcHelper
import org.artifactory.storage.security.service.AclStoreService
import org.artifactory.storage.security.service.UserGroupStoreService
import org.artifactory.util.HttpUtils

import org.artifactory.storage.db.servers.service.ArtifactoryServersCommonService

/* to enable logging ammend this to the end of artifactorys logback.xml
    <logger name="securityReplication">
        <level value="debug"/>
    </logger>
*/

//global variables
verbose = false
artHome = ctx.artifactoryHome.haAwareEtcDir
MASTER = null
DOWN_INSTANCE = []

//general artifactory plugin execution hook
executions {
    //Usage: curl -X GET http://localhost:8081/artifactory/api/plugins/execute/getUserDB
    //External/Internal Use
    getUserDB(httpMethod: 'GET'){
        try {
            message = new File(artHome, '/plugins/goldenFile.json').text
            log.debug("ALL: getUserDB is called giving my golden file DB: ${message}")
            status = 200
        } catch (FileNotFoundException ex) {
            message = "The golden user file either does not exist or has the wrong permissions"
            status = 400
        }
    }

    //Usage: curl -X POST http://localhost:8081/artifactory/api/plugins/execute/userDBSync
    //External Use
    userDBSync(httpMethod: 'POST') {
        def baseSnapShot = ["users":[:],"groups":[:],"permissions":[:]]
        def auth = RequestThreadLocal.context.get().requestThreadLocal.request.getHeader("Authorization")
        def secRepJson = new File(artHome, '/plugins/securityReplication.json')
        def slurped = null
        try {
            slurped = new JsonSlurper().parse(secRepJson)
        } catch (JsonException ex) {
            log.error("Problem parsing JSON: $ex.message")
            message = "Problem prasing JSON: $ex.message"
            status = 400
            return
        }
        def extracted = extract()
        syncAll(extracted, buildDiff(baseSnapShot, extracted), auth, 'dbSync', slurped, secRepJson)
        status = 200
    }

    //Usage: curl -X POST http://localhost:8081/artifactory/api/plugins/execute/distSecRep
    //External Use
    distSecRep(httpMethod: 'POST') {
        def auth = RequestThreadLocal.context.get().requestThreadLocal.request.getHeader("Authorization")
        def targetFile = new File(artHome, '/plugins/securityReplication.json')
        def slurped = null
        try {
            slurped = new JsonSlurper().parse(targetFile)
        } catch (JsonException ex) {
            message = "Problem parsing JSON: $ex.message"
            status = 400
            return
        }
        log.debug("Running distSecRep command: slurped: $slurped")
        pushData(slurped, auth)
    }

    //Usage: curl -X PUT http://localhost:8081/artifactory/api/plugins/execute/securityReplication -T <textfile>
    //External Use
    securityReplication(httpMethod: 'PUT') { ResourceStreamHandle body ->
        def targetFile = new File(artHome, '/plugins/securityReplication.json')
        try {
            targetFile.withOutputStream { it << body.inputStream }
            status = 200
        } catch (Exception ex) {
            message = "Problem writing file: $ex.message"
            status = 400
        }
    }

    //Usage: curl -X GET //http://localhost:8081/artifactory/api/plugins/execute/secRepJson
    //External Use
    secRepJson(httpMethod: 'GET') {
        try {
            message = new File(artHome, '/plugins/securityReplication.json').text
            status = 200
        } catch (FileNotFoundException ex) {
            message = "The security replication file either does not exist or has the wrong permissions"
            status = 400
        }
    }

    //Usage: curl -X GET http://localhost:8081/artifactory/api/plugins/execute/secRepDataGet?params=action=<action> -d <data>
    //Internal Use
    secRepDataGet(httpMethod: 'GET') { params ->
        log.debug("SLAVE: secRepDataGet is called")
        def action = params?.('action')?.getAt(0) as String
        def slavePatch = null
        def goldenDB = null
        def slurped = null
        def disasterRecovery = false
        def baseSnapShot = ["users":[:],"groups":[:],"permissions":[:]]

        def recentDump = new File(artHome, '/plugins/recent.json')
        def goldenFile = new File(artHome, '/plugins/goldenFile.json')
        def secRepJson = new File(artHome, "/plugins/securityReplication.json")

        try {
            slurped = new JsonSlurper().parse(secRepJson)
        } catch (JsonException ex) {
            message = "Problem parsing JSON: $ex.message"
            log.error("ALL: problem getting ${secRepJson} file")
            status = 400
            return
        }
        log.debug("SLAVE: secRepDataGet is called")

        disasterRecovery = checkDisasterRecovery(slurped, disasterRecovery, secRepJson)
        log.debug("SLAVE: disasterRecovery: ${disasterRecovery}")

        if (slurped.securityReplication.recovery == true && disasterRecovery == true) {
            log.debug("SLAVE: I am also in disaster recovery mode, nothing to give back")
            message = "[]"
            status = 200
            return
        }
        try {
            goldenDB = new JsonSlurper().parse(goldenFile)
        } catch (JsonException ex) {
            log.error("Problem parsing JSON: $ex.message")
            message = "Problem parsing JSON: $ex.message"
            status = 400
            return
        }
        if (verbose == true) {
            log.debug("SLAVE: goldenDB is ${goldenDB.toString()}")
        }
        def extracted = extract()
        recentDump.withWriter { new JsonBuilder(extracted).writeTo(it) }

        if (verbose == true) {
            log.debug("SLAVE: slaveExract is: ${extracted.getClass()}, ${extracted.toString()}")
        }

        if (action == 'diff'){
            log.debug("SLAVE: secRepDataGet diff")
            slavePatch = buildDiff(goldenDB, extracted)
        } else if (action == 'dbSync'){
            log.debug("SLAVE: secRepDataGet: dbSync")
            slavePatch = buildDiff(baseSnapShot, extracted)
        } else {
            log.debug("SLAVE: secRepDataGet bad action: ${data}")
            message = "secRepDataGet bad action"
            status = 400
            return
        }

        log.debug("SLAVE: slavePatch is: ${slavePatch.getClass()}, ${slavePatch.toString()}")
        slavePatch = new JsonBuilder(slavePatch)
        message = slavePatch
        status = 200
    }

    //Usage: curl -X POST http://localhost:8081/artifactory/api/plugins/execute/secRepDataPost -d <data>
    //Internal Use
    secRepDataPost(httpMethod: 'POST') { ResourceStreamHandle body ->
        log.debug("SLAVE: secRepDataPost is called")
        def slurped = null
        def goldenDB = null
        def tempSlaveGolden = null

        try {
            slurped = new JsonSlurper().parse(body.inputStream)
        } catch (JsonException ex) {
            log.error("SLAVE: error parsing JSON: $ex.message")
            message = "SLAVE: Problem parsing JSON: $ex.message"
            status = 400
            return
        }
        log.debug("SLAVE: slurped: ${slurped.toString()}")

        def goldenFile = new File(artHome, '/plugins/goldenFile.json')
        try {
            goldenDB = new JsonSlurper().parse(goldenFile)
        } catch (JsonException ex) {
            log.error("Problem parsing JSON: $ex.message")
            message = "Problem parsing JSON: $ex.message"
            status = 400
            return
        }

        if (verbose == true) {
            log.debug("SLAVE: original slave Golden is ${goldenDB.toString()}")
        }

        tempSlaveGolden = applyDiff(goldenDB, slurped)
        goldenDB = tempSlaveGolden

        if (verbose == true){
            log.debug("SLAVE: new slave Golden is ${goldenDB.toString()}")
        }

        writeToGoldenFile(goldenDB)
        //insert into DB here
        def recentDump = new File(artHome, '/plugins/recent.json')
        try {
            def extracted = new JsonSlurper().parse(recentDump)
            updateDatabase(extracted, goldenDB)
        } catch (JsonException ex) {
            log.error("Could not parse recent.json: $ex.message")
            message = "Could not parse recent.json: $ex.message"
            status = 400
            return
        }

        message = goldenDB.toString()
        status = 200
    }

    //TODO: Delete later, testing purposes only
    testSecurityDump(httpMethod: 'GET') { params ->
        status = 200
        message = new JsonBuilder(normalize(extract())).toPrettyString()
    }

    testDBUpdate() { params, ResourceStreamHandle body ->
        def json = new JsonSlurper().parse(body.inputStream)
        updateDatabase(null, json)
        status = 200
        message = "Completed, try dumping now"
    }
}

def getCronJob() {
    def defaultcron = "*/30 * * * * ?"
    def slurped = null
    def jsonFile = new File(artHome, "/plugins/securityReplication.json")
    try {
        slurped = new JsonSlurper().parse(jsonFile)
    } catch (JsonException ex) {
        log.error("ALL: problem getting ${jsonFile}, using default")
        return defaultcron
    }
    def cron_job = slurped?.securityReplication?.cron_job
    if (cron_job) {
        log.debug("ALL: config cron job is being set at: ${cron_job}")
        return cron_job
    } else {
        log.debug("ALL: cron job not configured, using default")
        return defaultcron
    }
}

//general artifactory cron job hook
jobs {
    securityReplicationWorker(cron: getCronJob()) {
        MASTER = null
        DOWN_INSTANCE = []
        def masterPatch = null
        def goldenDB = null
        def slurped = null
        def artStartTime = null
        def disasterRecovery = false

        def goldenFile = new File(artHome, '/plugins/goldenFile.json')
        def targetFile = new File(artHome, "/plugins/securityReplication.json")

        try {
            slurped = new JsonSlurper().parse(targetFile)
        } catch (JsonException ex) {
            log.error("ALL: problem getting ${targetFile}")
            return
        }

        disasterRecovery = checkDisasterRecovery(slurped, disasterRecovery, targetFile)
        log.debug("ALL: disasterRecovery is: ${disasterRecovery}")

        try {
            goldenDB = new JsonSlurper().parse(goldenFile)
        } catch (JsonException ex) {
            log.error("No goldenFile lets get the first goldenDB")
            goldenDB = extract()
            writeToGoldenFile(goldenDB)
        }

        def whoami = slurped.securityReplication.whoami
        def distList = slurped.securityReplication.urls
        def username = slurped.securityReplication.authorization.username
        def password = slurped.securityReplication.authorization.password
        def recovery = slurped.securityReplication.recovery
        def encoded = "$username:$password".getBytes().encodeBase64().toString()
        def auth = "Basic ${encoded}"

        log.debug("ALL: whoami: ${whoami}")
        log.debug("ALL: Master: ${MASTER}")
        log.debug("ALL: recovery: ${recovery}")

        findMaster(distList, whoami, auth)

        if (whoami != MASTER) {
            if (recovery == false){
                bringUp('SLAVE', whoami, auth, slurped, targetFile, null)
            } else if (recovery == true && disasterRecovery == true) {
                sleep(1000)
                runDisasterRecovery('SLAVE', auth, slurped, targetFile, goldenDB)
            } else {
                log.debug("SLAVE: I am a slave, going back to sleep")
            }
            return
        } else {
            log.debug("MASTER: I am the Master, starting to do work")

            log.debug("MASTER: Checking my slave instances")
            checkSlaveInstances(distList, whoami, auth)

            log.debug("MASTER: DOWN_INSTANCE: ${DOWN_INSTANCE.size()}, distList size: ${distList.size()}")
            if (DOWN_INSTANCE.size() >= (distList.size() - 1) && (MASTER == whoami)) {
                log.debug("MASTER: I'm all alone here, no need to do work")
                return
            }
            log.debug("MASTER: Down Instances again are: ${DOWN_INSTANCE}")

            if (recovery == false){
                bringUp('MASTER', whoami, auth, slurped, targetFile, goldenDB)
            } else if (recovery == true && disasterRecovery == true){
                log.debug("MASTER: I am recovering from a failure, please next slave given me a new golden DB")
                runDisasterRecovery('MASTER', auth, slurped, targetFile, goldenDB)
            } else {
                log.debug("MASTER: Not first time coming up, lets do some updates")
                def extracted = extract()
                masterPatch = buildDiff(goldenDB, extracted)
                log.debug("MASTER: masterPatch is: ${masterPatch}")

                //set new goldenDB with current db snapshot
                goldenDB = extracted

                if (verbose == true) {
                    log.debug("MASTER: new goldenDB is: ${goldenDB.toString()}")
                }

                writeToGoldenFile(goldenDB)

                syncAll(extracted, masterPatch, auth, 'diff', slurped, targetFile)
            }
        }
    }
}
//TODO: change to artifactory state(init, recovery, steady)
//add hashing
def checkDisasterRecovery(slurped, disasterRecovery, targetFile){
    def artStartTime = null
    def pluginStartTime = slurped.securityReplication.startTime

    if (ctx.beanForType(ArtifactoryServersCommonService).runningHaPrimary != null ) {
        artStartTime = ctx.beanForType(ArtifactoryServersCommonService).runningHaPrimary.startTime
    } else {
        artStartTime = ctx.beanForType(ArtifactoryServersCommonService).currentMember.startTime
    }
    log.debug("ALL: artStartTime: ${artStartTime}")
    log.debug("All: plugin startTime: ${pluginStartTime}")

    if (pluginStartTime == 0 || pluginStartTime == null) {
        slurped.securityReplication.startTime = artStartTime
        JsonBuilder builder = new JsonBuilder(slurped)
        targetFile.withWriter{builder.writeTo(it)}
        log.debug("ALL: init new plugin start time = ${artStartTime}")
        disasterRecovery = false
    } else if ( pluginStartTime < artStartTime ) {
        disasterRecovery = true
        log.debug("ALL: plugin is in disaster recovery mode, disasterRecovery: ${disasterRecovery}")
        slurped.securityReplication.startTime = artStartTime
        JsonBuilder builder = new JsonBuilder(slurped)
        targetFile.withWriter{builder.writeTo(it)}
        log.debug("ALL: recovery new plugin start time = ${artStartTime}")
    } else {
        disasterRecovery = false
        log.debug("ALL: steady state nothing to do")
    }
    return disasterRecovery
}

def writeToGoldenFile(goldenDB){
    def goldenFile = new File(artHome, '/plugins/goldenFile.json')

    if (verbose == true) {
        log.debug("TESTING: ${artHome}")
        log.debug("ALL: writing to goldenFile: ${goldenDB.toString()}")
    }

    try {
        JsonBuilder builder = new JsonBuilder(goldenDB)
        goldenFile.withWriter{builder.writeTo(it)}
    } catch (Exception ex){
        log.debug("ALL: failed to write to file $ex.message")
    }
}

def syncAll(recent, patch, auth, action, slurped, jsonFile){
    def goldenDB = null
    def goldenFile = new File(artHome, '/plugins/goldenFile.json')

    log.debug("MASTER: Going to slaves to get stuff action: ${action}, patch:${patch.getClass()}")
    def whoami = slurped.securityReplication.whoami
    def distList = slurped.securityReplication.urls
    def bigDiff = grabStuffFromSlaves(distList, patch, whoami, auth, action)
    log.debug("MASTER: The aggragated diff is: ${bigDiff}")

    if (bigDiff.isEmpty()){
        log.debug("MASTER: The aggragated diff is empty, no need to do anything")
        if (action == 'dbSync') {
            log.debug("MASTER: The DB's are all empty nothing to sync on init")
            writeToJson('MASTER', slurped, jsonFile, true)
        }
        return
    } else {
        def mergedPatch = merge(bigDiff)
        log.debug("MASTER: the merged golden patch is ${mergedPatch.getClass()}, ${mergedPatch.toString()}")

        //comvert tp JSON
        def jsonMergedPatch = new JsonBuilder(mergedPatch)
        log.debug("MASTER: jsonMergedPatch: ${jsonMergedPatch.getClass()} and ${jsonMergedPatch.toString()}")

        log.debug("MASTER: I gotta send the golden copy back to my slaves")
        sendSlavesGoldenCopy(distList, whoami, auth, jsonMergedPatch)

        try {
            goldenDB = new JsonSlurper().parse(goldenFile)
        } catch (JsonException ex) {
            log.error("Problem parsing JSON: $ex.message")
            return
        }

        log.debug("MASTER: goldenDB: ${goldenDB.getClass()}, mergedPatch: ${mergedPatch.getClass()}")
        goldenDB = applyDiff(goldenDB, mergedPatch)
        log.debug("MASTER: the merged golden db is ${goldenDB.toString()}")

        //Apply the new merged master golden into the DB
        updateDatabase(recent, goldenDB)
        if (action == 'dbSync'){
            writeToJson('MASTER', slurped, jsonFile, true)
        }
    }
}

def writeToJson(node, slurped, jsonFile, state){
    //update securityReplication.json file recovery to true
    slurped.securityReplication.recovery = state
    try {
        JsonBuilder builder = new JsonBuilder(slurped)
        jsonFile.withWriter{builder.writeTo(it)}
        log.debug("${node}: builder: ${builder.toString()}")
    } catch (Exception ex) {
        log.debug("${node}: failed to write ${jsonFile} with $ex.message")
    }
}

counter = 0
def remoteCall(baseurl, auth, method, data){
    if (verbose == true) {
        counter = counter + 1
        log.debug("COUNTER COUNTER COUNTER: ${counter}")
    }

    CloseableHttpClient httpclient = HttpClients.createDefault()
    def req = null
    switch(method) {
        case 'ping':
            def url = "${baseurl}/api/system/ping"
            req = new HttpGet(url)
            req.addHeader("User-Agent", HttpUtils.artifactoryUserAgent)
            req.addHeader("Authorization", auth)
            break
        case 'json':
            def url = "${baseurl}/api/plugins/execute/securityReplication"
            req = new HttpPut(url)
            req.addHeader("User-Agent", HttpUtils.artifactoryUserAgent)
            req.addHeader("Content-Type", "text/plain")
            req.addHeader("Authorization", auth)
            if (data){
                req.entity = new StringEntity(data)
            }
            break
        case 'plugin':
            def url = "${baseurl}/api/plugins/securityReplication"
            req = new HttpPut(url)
            req.addHeader("User-Agent", HttpUtils.artifactoryUserAgent)
            req.addHeader("Content-Type", "text/plain")
            req.addHeader("Authorization", auth)
            if (data){
                req.entity = new StringEntity(data)
            }
            break
        case 'data_retrieve':
            def url = "${baseurl}/api/plugins/execute/secRepDataGet?params=action=${data}"
            req = new HttpGet(url)
            req.addHeader("User-Agent", HttpUtils.artifactoryUserAgent)
            req.addHeader("Content-Type", "application/json")
            req.addHeader("Authorization", auth)
            break
        case 'data_send':
            def url = "${baseurl}/api/plugins/execute/secRepDataPost"
            req = new HttpPost(url)
            req.addHeader("User-Agent", HttpUtils.artifactoryUserAgent)
            req.addHeader("Content-Type", "application/json")
            req.addHeader("Authorization", auth)
            log.debug("MASTER: data being sent to slave is: ${data}")
            if (data){
                req.entity = new StringEntity(data.toString())
            }
            break
        case 'slaveDBSync':
            def url = "${baseurl}/api/plugins/execute/userDBSync"
            req = new HttpPost(url)
            req.addHeader("User-Agent", HttpUtils.artifactoryUserAgent)
            req.addHeader("Content-Type", "application/json")
            req.addHeader("Authorization", auth)
            break
        case 'recoverySync':
            def url = "${baseurl}/api/plugins/execute/getUserDB"
            req = new HttpGet(url)
            req.addHeader("User-Agent", HttpUtils.artifactoryUserAgent)
            req.addHeader("Content-Type", "application/json")
            req.addHeader("Authorization", auth)
            break
        default: throw new RuntimeException("Invalid method ${method}.")
    }
    CloseableHttpResponse resp = null
    try {
        resp = httpclient.execute(req)
        def ips = resp.getEntity().getContent()
        def statusCode = resp.getStatusLine().getStatusCode()
        return [ips.text, statusCode]
    } catch (e) {
        return [null, 406]
    } finally {
        httpclient?.close()
        resp?.close()
    }
}

def pushData(slurped, auth) {
    def pluginFile = new File(artHome, '/plugins/securityReplication.groovy')
    log.debug("Running distSecRep command: inside pushData")

    if (!pluginFile || !pluginFile.exists()) {
        log.error("GENERAL: ${pluginFile} not found")
        return
    }

    def distList = slurped.securityReplication.urls
    log.debug("Running distSecRep command: distList: ${distList}")
    distList.each{
        if (slurped.securityReplication.whoami != it) {

            //check the health status of each artifactory instance
            log.debug("checking health of ${it}")
            def resp = remoteCall(it, auth, 'ping', null)
            if (resp[1] != 200) {
                throw new RuntimeException("Health Check Failed: ${it}: HTTP error code: " + resp[1])
            }

            slurped.securityReplication.whoami = "${it}"
            def builder = new JsonBuilder(slurped)

            //push plugin to all instances
            log.debug("sending plugin to ${it} instance")
            resp = remoteCall(it, auth, 'plugin', pluginFile.text)
            if (resp[1] != 200) {
                throw new RuntimeException("PLUGIN Push Failed: ${it}: HTTP error code: " + resp[1])
            }

            //push json to all instances
            log.debug("sending json file to ${it} instance")
            resp = remoteCall(it, auth, 'json', builder.toString())
            if (resp[1] != 200) {
                throw new RuntimeException("JSON Push Failed: ${it}: HTTP errorcode: " + resp[1])
            }
        }
    }
}

def sendSlavesGoldenCopy(distList, whoami, auth, mergedPatch){
    //convert array to string
    log.debug("MASTER: the merged patch is: ${mergedPatch.getClass()}, ${mergedPatch.toString()}")

    for (i = 0; i < distList.size(); i++){
        def instance = distList[i]
        if ((MASTER == instance) && (MASTER == whoami)) {
            log.debug("MASTER: This is myself (the Master), I don't need to send stuff to myself")
        } else if (DOWN_INSTANCE.contains(instance)) {
            log.debug("MASTER: This instance: ${instance} is down, I don't need to give it stuff")
        } else {
            log.debug("MASTER: Sending mergedPatch to ${instance}")
            def resp = remoteCall(instance, auth, 'data_send', mergedPatch)
            if (resp[1] != 200) {
                log.error("MASTER: Error sending to ${instance} the mergedPatch, statusCode: ${resp[1]}")
                break
            }
            log.debug("MASTER: Sent Data, Slave responds: ${resp[0].toString()}")
        }
    }
}

def findNextSlave(node, distList, auth, whoami){
    def recoverySlave = null
    log.debug("${node}: find next slave who is not the master to get golden db from")
    for (i = 0; i < distList.size(); i++){
        def instance = distList[i]
        if (MASTER == instance && MASTER == whoami){
            log.debug("${node}: this is me, I need the recovery DB from the next up slave")
        } else if (DOWN_INSTANCE.contains(instance)){
            log.debug("${node}: ${instance} is down, can't get anything from this slave")
        } else {
            def resp = remoteCall(instance, auth, 'ping', null)
            if (resp[1] != 200){
                log.error("${node}: ping failed: ${MASTER}: HTTP error code: " + resp[1])
            } else {
                log.debug("${node}: ${instance} is up, let's get the recovery DB from this slave")
                recoverySlave = instance
                break
            }
        }
    }
    return recoverySlave
}

def runDisasterRecovery(node, auth, slurped, jsonFile, goldenDB){
    def recoverySlave = null
    def resp = null
    def distList = slurped.securityReplication.urls
    def retryCount = 0

    if (node == 'SLAVE'){
        log.debug("${node}: I am recovering from a failure, please send me the master golden db")
        resp = remoteCall(MASTER, auth, 'recoverySync', null)
        if (resp[1] != 200) {
            log.error("${node}: recoverySync failed: ${MASTER} retry(${retryCount}): HTTP error code: " + resp[1])
            if (retryCount >= 3){
                writeToJson('SLAVE', slurped, jsonFile, false)
            } else {
                retryCount = retryCount + 1
            }
            return
        }

        try {
            goldenDB = new JsonSlurper().parseText(resp[0].toString())
        } catch (JsonException ex) {
            log.error("${node}: Problem parsing JSON: $ex.message")
            return
        }

        writeToGoldenFile(goldenDB)
        updateDatabase(null, goldenDB)
    } else if (node == 'MASTER'){
        log.debug("${node}: I am recovering from a failure, please next slave given me a new golden DB")

        //find next slave who is not the master to get golden db from
        if (distList.size() > 1) {
            recoverySlave = findNextSlave('MASTER', distList, auth, slurped.securityReplication.whoami)
            resp = remoteCall(recoverySlave, auth, 'recoverySync', null)

            if (resp[1] != 200) {
                log.error("${node}: recoverySync failed: ${MASTER}: retry(${retryCount}), HTTP error code: " + resp[1])
                if (retryCount >= 3) {
                    writeToJson('MASTER', slurped, jsonFile, false)
                } else {
                    retryCount = retryCount + 1
                }
                return
            }

            try {
                goldenDB = new JsonSlurper().parseText(resp[0].toString())
            } catch (JsonException ex) {
                log.error("${node}: Problem parsing JSON: $ex.message")
                return
            }

            writeToGoldenFile(goldenDB)
            updateDatabase(null, goldenDB)
        } else {
            log.debug("${node}: there is only me, i'll start from scratch")
            goldenDB = extract()
            writeToGoldenFile(goldenDB)
            return
        }
    } else {
        log.error("ALL: invalid node: ${node}")
    }
}

def bringUp(node, whoami, auth, slurped, jsonFile, goldenDB){
    def baseSnapShot = ["users":[:],"groups":[:],"permissions":[:]]
    if (node == 'SLAVE'){
        log.debug("${node}: This is my first time I'm coming up, telling master to sync everything for me")
        sleep(1000) //this sleep is here to avoid initial bring up timing colisions
        def resp = remoteCall(MASTER, auth, 'slaveDBSync', null)
        if (resp[1] != 200) {
            throw new RuntimeException("${node}: slaveDBSync Failed: ${MASTER}: HTTP error code: " + resp[1])
        }
        writeToJson(node, slurped, jsonFile, true)
    } else if (node == 'MASTER'){
        log.debug("${node}: first time comming up, lets sync everything existing first")

        writeToGoldenFile(goldenDB)

        log.debug("${node}: goldenDB is: ${goldenDB.toString()}")

        log.debug("${node}: baseSnapShot: ${baseSnapShot.getClass()}, ${baseSnapShot.toString()}")
        def extracted = extract()
        def dbMasterPatch = buildDiff(baseSnapShot, extracted)
        log.debug("${node}: dbMasterPatch: ${dbMasterPatch.getClass()}, ${dbMasterPatch.toString()}")

        log.debug("${node}: going to slaves to grab everything")
        syncAll(extracted, dbMasterPatch, auth, 'dbSync', slurped, jsonFile)
    } else {
        log.error("ALL: invalid node ${node}")
    }
}

def findMaster(distList, whoami, auth){
    if (whoami == MASTER) {
        log.debug("MASTER: I am the Master")
    } else {
        log.debug("ALL: I don't know who I am, checking if Master is up")

        //check if master is up
        for (i = 0; i < distList.size(); i++) {
            def instance = distList[i]

            if (instance == whoami) {
                log.debug("MASTER: I am the Master setting Master")
                MASTER = whoami
                break
            }

            log.debug("ALL: Checking if ${instance} is up")
            def resp = remoteCall(instance, auth, 'ping', null)
            log.debug("ALL: ping statusCode: ${resp[1]}")
            if (resp[1] != 200) {
                log.warn("ALL: ${instance} instance is down, finding new master\r")
                if (!DOWN_INSTANCE.contains(instance)){
                    DOWN_INSTANCE << instance
                }
            } else {
                log.debug("ALL: ${instance} is up, setting MASTER \r")
                MASTER = instance
                if (DOWN_INSTANCE.contains(instance)) {
                    DOWN_INSTANCE.remove(instance)
                }
                break
            }
        }
    }
    log.debug("ALL: Down Instances are: ${DOWN_INSTANCE}")
}

def checkSlaveInstances(distList, whoami, auth){
    for (i = 0; i < distList.size(); i++){
        def instance = distList[i]
        if ((MASTER == instance) && (MASTER == whoami)){
            log.debug("MASTER: This is myself (the Master), I'm up, setting MASTER")
            MASTER = whoami
        } else {
            def resp = remoteCall(instance, auth, 'ping', null)
            log.debug("MASTER: ping statusCode: ${resp[1]}")
            if (resp[1] != 200) {
                log.warn("MASTER: Slave: ${instance} instance is down, adding to DOWN_INSTANCE list")
                if (!DOWN_INSTANCE.contains(instance)){
                    DOWN_INSTANCE << instance
                }
            } else {
                log.debug("MASTER: Slave: ${instance} instance is up")
                if (DOWN_INSTANCE.contains(instance)){
                    DOWN_INSTANCE.remove(instance)
                }
            }
        }
    }
}

def grabStuffFromSlaves(distList, masterDiff, whoami, auth, action){
    def bigDiff = []

    log.debug("MASTER: masterDiff is ${masterDiff.getClass()}, ${masterDiff.toString()}")
    //only add masterDiff if it not empty
    if (!masterDiff.isEmpty()){
        bigDiff << masterDiff
    }

    for (i = 0; i < distList.size(); i++) {
        def instance = distList[i]
        if (MASTER == instance && MASTER == whoami){
            log.debug("MASTER: This is myself (the Master), don't need to do any work here")
        } else if (DOWN_INSTANCE.contains(instance)) {
            log.debug("MASTER: ${instance} is down, no need to do anything")
        } else {
            log.debug("MASTER: Accessing ${instance}, give me your stuff")
            def resp = remoteCall(instance, auth, 'data_retrieve', action)
            if (resp[1] != 200) {
                log.error("MASTER: Error accessing ${instance}, failed to retrieve data")
                break
            }

            //get response message
            newDiff = resp[0]

            //removing brackets to detect null strings
            def indexOfOpenBracket = newDiff.indexOf("[")
            def indexOfLastBracket = newDiff.lastIndexOf("]")
            tempNewDiff = newDiff.substring(indexOfOpenBracket+1, indexOfLastBracket)

            newDiff = new JsonSlurper().parseText(newDiff.toString())

            log.debug("MASTER: Slaves stuff in newDiff: ${newDiff.getClass()}, ${newDiff.toString()}")
            log.debug("MASTER: tempNewDiff without bracket: ${tempNewDiff.toString()}")
            if (!tempNewDiff.isEmpty()) {
                bigDiff << newDiff
            } else {
                log.debug("MASTER: newDiff is empty moving on")
            }
        }
    }
    log.debug("MASTER: slave bigdiff return: ${bigDiff.toString()}")
    return bigDiff
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

// internal structure for security data:
// - what's the database structure for this?
//   - ACES: ACE_ID ACL_ID MASK USER_ID GROUP_ID
//   - ACLS: ACL_ID PERM_TARGET_ID
//   - GROUPS: GROUP_ID GROUP_NAME DESCRIPTION DEFAULT_NEW_USER
//             REALM REALM_ATTRIBUTES
//   - PERMISSION_TARGETS: PERM_TARGET_ID PERM_TARGET_NAME INCLUDES EXCLUDES
//   - PERMISSION_TARGET_REPOS: PERM_TARGET_ID REPO_KEY
//   - USERS: USER_ID USERNAME PASSWORD SALT EMAIL GEN_PASSWORD_KEY ADMIN
//            ENABLED UPDATABLE_PROFILE PRIVATE_KEY PUBLIC_KEY BINTRAY_AUTH
//            LOCKED CREDENTIALS_EXPIRED
//   - USERS_GROUPS: USER_ID GROUP_ID REALM
//   - USER_PROPS: USER_ID PROP_KEY PROP_VALUE
// - final setup:
//   - Permission: name [includes] [excludes] [repos]
//   - Group: name description isdefault realm realmattrs {permissions}
//   - User: name password salt email passkey admin enabled updatable
//           privatekey publickey bintray locked expired
//           [groups] {properties} {permissions}

writesort = { left, right ->
    def opord = [';+': 0, ':+': 1, ':~': 2, ':-': 3, ';-': 4]
    def typord = ['permissions': 0, 'groups': 1, 'users': 2]
    if (left[1] != right[1]) return opord[left[1]] <=> opord[right[1]]
    if (left[0][0] != right[0][0]) {
        def result = typord[left[0][0]] <=> typord[right[0][0]]
        return left[1] == ';-' ? -result : result
    }
    return diffsort(left, right)
}

def select(jdbcHelper, query, callback) {
    def rs = null
    try {
        rs = jdbcHelper.executeSelect(query)
        while (rs.next()) callback(rs)
    } finally {
        if (rs) DbUtils.close(rs)
    }
}

def extract() {
    def jdbcHelper = ctx.beanForType(JdbcHelper)
    def result = [:], useraces = null, groupaces = null, usergroups = null
    // get the filter settings from the config file
    def filter = null
    def secRepJson = new File(artHome, '/plugins/securityReplication.json')
    try {
        def slurped = new JsonSlurper().parse(secRepJson)
        filter = slurped?.securityReplication?.filter ?: 3
    } catch (JsonException ex) {
        filter = 3
    }
    if (filter >= 3) {
        // extract from permission_target_repos
        def permrepos = HashMultimap.create()
        def permrepoquery = 'SELECT perm_target_id, repo_key FROM'
        permrepoquery += ' permission_target_repos'
        select(jdbcHelper, permrepoquery) {
            permrepos.put(it.getLong(1), it.getString(2))
        }
        // extract from permission_targets
        def perms = [:]
        def permnames = [:]
        def permquery = 'SELECT perm_target_id, perm_target_name, includes,'
        permquery += ' excludes FROM permission_targets'
        select(jdbcHelper, permquery) {
            def perm = [:]
            def id = it.getLong(1)
            def name = it.getString(2)
            perm.includes = it.getString(3)?.split(',') as List ?: []
            perm.excludes = it.getString(4)?.split(',') as List ?: []
            perm.repos = permrepos.get(id) ?: []
            permnames[id] = name
            perms[name] = perm
        }
        result['permissions'] = perms
        // extract from aces
        useraces = [:]
        groupaces = [:]
        def acequery = 'SELECT e.user_id, e.group_id, e.mask, l.perm_target_id'
        acequery += ' FROM aces e INNER JOIN acls l ON (e.acl_id = l.acl_id)'
        select(jdbcHelper, acequery) {
            def userid = it.getLong(1)
            def groupid = it.getLong(2)
            def privstr = ''
            def privs = it.getInt(3)
            if (privs &  8) privstr += 'd'
            if (privs & 16) privstr += 'm'
            if (privs &  4) privstr += 'n'
            if (privs &  1) privstr += 'r'
            if (privs &  2) privstr += 'w'
            def perm = permnames[it.getLong(4)]
            if (perm != null && userid != null) {
                if (!(userid in useraces)) useraces[userid] = [:]
                useraces[userid][perm] = privstr
            }
            if (perm != null && groupid != null) {
                if (!(groupid in groupaces)) groupaces[groupid] = [:]
                groupaces[groupid][perm] = privstr
            }
        }
    }
    if (filter >= 2) {
        // extract from groups
        def groups = [:]
        def groupnames = [:]
        def groupquery = 'SELECT group_id, group_name, description,'
        groupquery += ' default_new_user, realm, realm_attributes FROM groups'
        select(jdbcHelper, groupquery) {
            def group = [:]
            def id = it.getLong(1)
            def name = it.getString(2)
            group.description = it.getString(3)
            group.isdefault = it.getBoolean(4)
            group.realm = it.getString(5)
            group.realmattrs = it.getString(6)
            if (filter >= 3) group.permissions = groupaces[id] ?: [:]
            groupnames[id] = name
            groups[name] = group
        }
        result['groups'] = groups
        // extract from users_groups
        usergroups = HashMultimap.create()
        def usergroupquery = 'SELECT user_id, group_id FROM users_groups'
        select(jdbcHelper, usergroupquery) {
            usergroups.put(it.getLong(1), groupnames[it.getLong(2)])
        }
    }
    if (filter >= 1) {
        // extract from user_props
        def userprops = [:]
        def userpropquery = 'SELECT user_id, prop_key, prop_value'
        userpropquery += ' FROM user_props'
        select(jdbcHelper, userpropquery) {
            def userid = it.getLong(1)
            if (!(userid in userprops)) userprops[userid] = [:]
            userprops[userid][it.getString(2)] = it.getString(3)
        }
        // extract from users
        def users = [:]
        def userquery = 'SELECT user_id, username, password, salt, email,'
        userquery += ' gen_password_key, admin, enabled, updatable_profile,'
        userquery += ' private_key, public_key, bintray_auth, locked,'
        userquery += ' credentials_expired FROM users'
        select(jdbcHelper, userquery) {
            def user = [:]
            def id = it.getLong(1)
            def name = it.getString(2)
            user.password = it.getString(3)
            user.salt = it.getString(4)
            user.email = it.getString(5)
            user.passkey = it.getString(6)
            user.admin = it.getBoolean(7)
            user.enabled = it.getBoolean(8)
            user.updatable = it.getBoolean(9)
            user.privatekey = it.getString(10)
            user.publickey = it.getString(11)
            user.bintray = it.getString(12)
            user.locked = it.getBoolean(13)
            user.expired = it.getBoolean(14)
            if (filter >= 2) user.groups = usergroups.get(id) ?: []
            user.properties = userprops[id] ?: [:]
            if (filter >= 3) user.permissions = useraces[id] ?: [:]
            users[name] = user
        }
        result['users'] = users
    }
    return result
}

def getdbid(jdbcHelper, type, name) {
    def rs = null
    def data = ['users': ['user_id', 'users', 'username'],
                'groups': ['group_id', 'groups', 'group_name'],
                'permissions': ['perm_target_id', 'permission_targets',
                                'perm_target_name']]
    def (idfield, table, namefield) = data[type]
    def query = "SELECT $idfield FROM $table WHERE $namefield = ?"
    try {
        rs = jdbcHelper.executeSelect(query, name)
        if (rs.next()) return rs.getLong(1)
    } finally {
        DbUtils.close(rs)
    }
}

def makeMask(privs) {
    if (!privs) return 0
    def mask = 0
    if (privs.contains('r')) mask += 1
    if (privs.contains('w')) mask += 2
    if (privs.contains('n')) mask += 4
    if (privs.contains('d')) mask += 8
    if (privs.contains('m')) mask += 16
    return mask
}

def update(ptch) {
    def userrows = ['password': 'password', 'salt': 'salt', 'email': 'email',
                    'passkey': 'gen_password_key', 'admin': 'admin',
                    'enabled': 'enabled', 'updatable': 'updatable_profile',
                    'privatekey': 'private_key', 'publickey': 'public_key',
                    'bintray': 'bintray_auth', 'locked': 'locked',
                    'expired': 'credentials_expired']
    def grouprows = ['description': 'description',
                     'isdefault': 'default_new_user', 'realm': 'realm',
                     'realmattrs': 'realm_attributes']
    def dbserv = ctx.beanForType(DbService)
    def aclserv = ctx.beanForType(AclStoreService)
    def userserv = ctx.beanForType(UserGroupStoreService)
    def jdbcHelper = ctx.beanForType(JdbcHelper)
    for (line in ptch) {
        def (path, oper, key, val) = line
        def pathsize = path.size()
        if (pathsize == 3 && oper in [':+', ':-'] &&
            path[0] == 'permissions' && path[2] in ['includes', 'excludes']) {
            // permission targets
            def ls = null, rs = null
            def query = "SELECT ${path[2]} FROM permission_targets WHERE"
            query += " perm_target_name = ?"
            try {
                rs = jdbcHelper.executeSelect(query, path[1])
                if (rs.next()) ls = rs.getString(1)?.split(',') as List ?: []
            } finally {
                DbUtils.close(rs)
            }
            if (oper == ':+') ls << key
            else if (oper == ':-') ls -= key
            ls = ls.sort().unique().join(',') ?: null
            query = "UPDATE permission_targets SET ${path[2]} = ? WHERE"
            query += " perm_target_name = ?"
            jdbcHelper.executeUpdate(query, ls, path[1])
        } else if ((pathsize == 3 && oper in [';+', ';-'] ||
                    pathsize == 4 && oper == ':~') &&
                   path[0] in ['users', 'groups'] && path[2] == 'permissions') {
            // aces
            def ownerfield = path[0] == 'users' ? 'user_id' : 'group_id'
            def ownerid = getdbid(jdbcHelper, path[0], path[1])
            def permkey = oper == ':~' ? path[3] : key
            def permid = getdbid(jdbcHelper, 'permissions', permkey)
            def aceid = null
            if (oper != ';+') {
                def rs = null
                def query = "SELECT e.ace_id FROM aces e INNER JOIN"
                query += " acls l ON (e.acl_id = l.acl_id) WHERE"
                query += " l.perm_target_id = ? AND e.$ownerfield = ?"
                try {
                    rs = jdbcHelper.executeSelect(query, permid, ownerid)
                    if (rs.next()) aceid = rs.getLong(1)
                } finally {
                    DbUtils.close(rs)
                }
            }
            if (oper == ';+') {
                def mask = makeMask(val)
                def aclid = null, rs = null
                def query = "SELECT acl_id FROM acls WHERE perm_target_id = ?"
                try {
                    rs = jdbcHelper.executeSelect(query, permid)
                    if (rs.next()) aclid = rs.getLong(1)
                } finally {
                    DbUtils.close(rs)
                }
                aceid = dbserv.nextId()
                def uid = null, gid = null
                if (path[0] == 'users') uid = ownerid
                else if (path[0] == 'groups') gid = ownerid
                query = "INSERT INTO aces VALUES(?,?,?,?,?)"
                jdbcHelper.executeUpdate(query, aceid, aclid, mask, uid, gid)
            } else if (oper == ';-') {
                def query = "DELETE FROM aces WHERE ace_id = ?"
                jdbcHelper.executeUpdate(query, aceid)
            } else if (oper == ':~') {
                def mask = makeMask(key)
                def query = "UPDATE aces SET mask = ? WHERE ace_id = ?"
                jdbcHelper.executeUpdate(query, mask, aceid)
            }
        } else if (pathsize == 1 && oper in [';+', ';-'] &&
                   path[0] in ['users', 'groups', 'permissions']) {
            // users, groups, and permissions
            if (oper == ';+' && path[0] == 'users') {
                def user = new UserImpl(key)
                user.password = new SaltedPassword(val.password, val.salt)
                user.email = val.email
                user.genPasswordKey = val.passkey
                user.admin = val.admin
                user.enabled = val.enabled
                user.updatableProfile = val.updatable
                user.privateKey = val.privatekey
                user.publicKey = val.publickey
                user.bintrayAuth = val.bintray
                user.locked = val.locked
                user.credentialsExpired = val.expired
                for (group in val.groups) user.addGroup(group)
                for (prop in val.properties.entrySet()) {
                    user.putUserProperty(prop.key, prop.value)
                }
                userserv.createUserWithProperties(user, true)
                for (perm in val.permissions?.entrySet()) {
                    def mask = makeMask(perm.value)
                    def acl = new AclImpl(aclserv.getAcl(perm.key))
                    acl.mutableAces << new AceImpl(key, false, mask)
                    aclserv.updateAcl(acl)
                }
            } else if (oper == ';-' && path[0] == 'users') {
                aclserv.removeAllUserAces(key)
                userserv.deleteUser(key)
            } else if (oper == ';+' && path[0] == 'groups') {
                def group = new GroupImpl(
                    key, val.description, val.isdefault, val.realm,
                    val.realmattrs)
                userserv.createGroup(group)
                for (perm in val.permissions?.entrySet()) {
                    def mask = makeMask(perm.value)
                    def acl = new AclImpl(aclserv.getAcl(perm.key))
                    acl.mutableAces << new AceImpl(key, true, mask)
                    aclserv.updateAcl(acl)
                }
            } else if (oper == ';-' && path[0] == 'groups') {
                aclserv.removeAllGroupAces(key)
                userserv.deleteGroup(key)
            } else if (oper == ';+' && path[0] == 'permissions') {
                def perm = new PermissionTargetImpl(
                    key, val.repos, val.includes, val.excludes)
                aclserv.createAcl(new AclImpl(perm))
            } else if (oper == ';-' && path[0] == 'permissions') {
                aclserv.deleteAcl(key)
            }
        } else if ((pathsize == 3 && oper in [';+', ';-'] ||
                    pathsize == 4 && oper == ':~') &&
                   path[0] == 'users' && path[2] == 'properties') {
            // user properties, including API keys
            def userid = getdbid(jdbcHelper, 'users', path[1])
            if (oper == ';+') {
                def query = "INSERT INTO user_props VALUES(?,?,?)"
                jdbcHelper.executeUpdate(query, userid, key, val)
            } else if (oper == ';-') {
                def query = "DELETE FROM user_props WHERE"
                query += " user_id = ? AND prop_key = ?"
                jdbcHelper.executeUpdate(query, userid, key)
            } else if (oper == ':~') {
                def query = "UPDATE user_props SET prop_value = ? WHERE"
                query += " user_id = ? AND prop_key = ?"
                jdbcHelper.executeUpdate(query, key, userid, path[3])
            }
        } else if (pathsize == 3 && oper in [':+', ':-'] &&
                   path[0] == 'users' && path[2] == 'groups') {
            // user/group memberships
            def userid = getdbid(jdbcHelper, 'users', path[1])
            def groupid = getdbid(jdbcHelper, 'groups', key)
            if (oper == ':+') {
                def query = "INSERT INTO users_groups VALUES(?,?,?)"
                jdbcHelper.executeUpdate(query, userid, groupid, null)
            } else if (oper == ':-') {
                def query = "DELETE FROM users_groups WHERE"
                query += " user_id = ? AND group_id = ?"
                jdbcHelper.executeUpdate(query, userid, groupid)
            }
        } else if (pathsize == 3 && oper in [':+', ':-'] &&
                   path[0] == 'permissions' && path[2] == 'repos') {
            // repository lists for permission configurations
            def permid = getdbid(jdbcHelper, 'permissions', path[1])
            if (oper == ':+') {
                def query = "INSERT INTO permission_target_repos VALUES(?,?)"
                jdbcHelper.executeUpdate(query, permid, key)
            } else if (oper == ':-') {
                def query = "DELETE FROM permission_target_repos WHERE"
                query += " perm_target_id = ? AND repo_key = ?"
                jdbcHelper.executeUpdate(query, permid, key)
            }
        } else if (pathsize == 3 && oper == ':~' &&
                   (path[0] == 'users' && path[2] in userrows ||
                    path[0] == 'groups' && path[2] in grouprows)) {
            // simple user/group attributes (description, is admin, etc)
            def id = null, attr = null
            if (path[0] == 'users') {
                id = 'username'
                attr = userrows[path[2]]
            } else if (path[0] == 'groups') {
                id = 'group_name'
                attr = grouprows[path[2]]
            }
            def setvalue = key instanceof Boolean ? (key ? 1 : 0) : key
            def query = "UPDATE ${path[0]} SET $attr = ? WHERE $id = ?"
            jdbcHelper.executeUpdate(query, setvalue, path[1])
        }
    }
}

def diff(path, ptch, oldver, newver) {
    if (((oldver == null || oldver instanceof CharSequence) &&
         (newver == null || newver instanceof CharSequence)) ||
        ((oldver == null || oldver instanceof Boolean) &&
         (newver == null || newver instanceof Boolean)) ||
        ((oldver == null || oldver instanceof Number) &&
         (newver == null || newver instanceof Number))) {
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
    // `patches` is a list of patchsets (a patchset is a list of changes).
    // `result` should be a single patchset, which is all the patchsets in
    // `patches` concatenated.
    patches.each { result.addAll(it) }
    // Uniquify `result` by removing all but the first 'equivalent' element.
    // Since `patches` is passed to this function in priority order, the highest
    // priority result will always be kept.
    result = result.unique {
        // If we're dealing with a simple value update, only compare the path
        // and the operator.
        if (it[1] == ':~') return it[0..1]
        // If we're dealing with a map +/-, compare the path, operator, and key.
        // If we're dealing with a list +/-, compare the path, operator, and
        // value.
        else return it[0..2]
    }
    // Sort the remaining data and delete any unnecessary changes. Mostly this
    // just comes down to pruning changes that update parts of the tree that are
    // deleted by other changes. Say if user 'A' is removed, but user 'A's
    // 'password' property is updated, the update would be pruned because the
    // user is being deleted anyway.
    result = result.sort(diffsort).unique { l, r ->
        // If neither of the changes are map deletes, don't bother.
        if (l[1] != ';-' && r[1] != ';-') return 1
        // If either change is a map change, put the key at the end of the path.
        def lp = l[1][0] == ';' ? l[0] + l[2] : l[0]
        def rp = r[1][0] == ';' ? r[0] + r[2] : r[0]
        // We want the map delete with the shorter path to be on the left,
        // otherwise it wouldn't be possible to tell whether pruning should
        // occur. If they're both map deletes, put the shorter one on the left,
        // otherwise put the map delete on the left.
        if (l[1] == r[1] && lp.size() > rp.size() || l[1] != ';-') {
            (l, lp, r, rp) = [r, rp, l, lp]
        }
        // If the left path is longer, we don't have a valid prune. Also, if the
        // left path is not an ancestor of the right path, we don't have a valid
        // prune.
        if (lp.size() > rp.size() || lp != rp[0..<lp.size()]) return 1
        // We now know that the left path is both an ancestor of the right path
        // and a map delete, so prune the change.
        return 0
    }
    return result
}

def buildDiff(oldver, newver) {
    def ptch = []
    diff([], ptch, normalize(oldver), normalize(newver))
    return ptch
}

def applyDiff(oldver, ptch) {
    return patch([], ptch.sort(diffsort), normalize(oldver))
}

def updateDatabase(oldver, newver) {
    // current state of the database (might have changed since oldver)
    def currver = extract()
    if (oldver == null) oldver = currver
    // newver from oldver
    def newdiff = buildDiff(oldver, newver)
    // currver from oldver
    def currdiff = buildDiff(oldver, currver)
    // newver + currver from oldver
    def truediff = merge([currdiff, newdiff])
    // newver + currver
    def truever = applyDiff(oldver, truediff)
    // newver + currver from currver
    def finaldiff = buildDiff(currver, truever).sort(writesort)
    // log.error(new JsonBuilder(finaldiff).toPrettyString())
    // apply this to the database for the correct result
    update(finaldiff)
}

def getHash(json) {
    def digest = MessageDigest.getInstance("SHA-1")
    def hash = digest.digest(new JsonBuilder(normalize(json)).toString().bytes)
    return hash.encodeHex().toString()
}
