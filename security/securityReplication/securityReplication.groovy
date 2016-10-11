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
import java.security.MessageDigest
import java.sql.Connection
import java.sql.SQLException

import org.apache.http.client.methods.*
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.HttpClients
import org.apache.http.HttpResponse
import org.apache.http.NameValuePair
import org.apache.http.HttpStatus
import org.apache.http.HttpResponseFactory
import org.apache.http.HttpVersion
import org.apache.http.impl.DefaultHttpResponseFactory
import org.apache.http.message.BasicStatusLine
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.message.BasicNameValuePair
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.client.HttpClient
import org.apache.http.params.HttpParams
import org.apache.http.client.config.RequestConfig
import org.apache.http.impl.client.CloseableHttpClient

import org.artifactory.util.HttpClientConfigurator
import org.artifactory.util.HttpUtils

import org.springframework.jdbc.datasource.DataSourceUtils
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.transaction.support.TransactionSynchronizationManager

import org.artifactory.model.xstream.security.AceImpl
import org.artifactory.model.xstream.security.AclImpl
import org.artifactory.model.xstream.security.GroupImpl
import org.artifactory.model.xstream.security.PermissionTargetImpl
import org.artifactory.model.xstream.security.UserImpl
import org.artifactory.resource.ResourceStreamHandle
import org.artifactory.security.SaltedPassword
import org.artifactory.storage.db.DbService
import org.artifactory.storage.db.util.DbUtils
import org.artifactory.storage.db.util.JdbcHelper
import org.artifactory.storage.security.service.AclStoreService
import org.artifactory.storage.security.service.UserGroupStoreService
import static org.artifactory.repo.RepoPathFactory.create

import groovy.json.*

/* to enable logging ammend this to the end of artifactorys logback.xml
    <logger name="securityReplication">
        <level value="debug"/>
    </logger>
*/

//global variables
memRecovery = false
retryCount = 0
def baseSnapShot = ["users":[:],"groups":[:],"permissions":[:]]
def artHome = ctx.artifactoryHome.haAwareEtcDir

//general artifactory plugin execution hook
executions {
    /*
    -----------------------------------------------------------------------------
    Execution:
    -----------------------------------------------------------------------------
    Usage: curl -X POST http://localhost:8081/artifactory/api/plugins/execute/userDBSync
    -----------------------------------------------------------------------------
    User: Admin
    -----------------------------------------------------------------------------
    Input: None
    -----------------------------------------------------------------------------
    Output: None
    -----------------------------------------------------------------------------
    */
    userDBSync(httpMethod: 'POST') {
        def auth = org.artifactory.request.RequestThreadLocal.context.get().requestThreadLocal.request.getHeader("authorization")

        def secRepJson = new File(artHome, '/plugins/securityReplication.json')
        if (!secRepJson || !secRepJson.exists()) {
            message = "Error: no securityReplication.json file found"
            status = 400
        } else {
            def slurped = null
            try {
                slurped = new JsonSlurper().parseText(secRepJson.text)
            } catch (groovy.json.JsonException ex) {
                log.error("Problem parsing JSON: ${ex}.message")
                message = "Problem prasing JSON: ${ex}.message"
                status = 400
                return
            }

            def whoami = slurped.securityReplication.whoami
            def distList = slurped.securityReplication.urls
            syncAll(distList, buildDiff(baseSnapShot, extract()), whoami, auth, 'dbSync', slurped, secRepJson)
        }
    }

    /*
    -----------------------------------------------------------------------------
    Execution: 
    -----------------------------------------------------------------------------
    Usage: curl -X POST http://localhost:8081/artifactory/api/plugins/execute/recoverySync
    -----------------------------------------------------------------------------
    User: Admin
    -----------------------------------------------------------------------------
    Input: None
    -----------------------------------------------------------------------------
    Output: None
    -----------------------------------------------------------------------------
    */
    recoverySync(httpMethod: 'POST') {
        def goldenFile = new File(artHome, '/plugins/goldenFile.json')
        if (!goldenFile || !goldenFile.exists()){
            log.debug("No goldenFile something is wrong")
            message = "No goldenFile something is wrong"
            status = 400
            return
        } else {
            try {
                slurped = new JsonSlurper().parseText(goldenFile.text)
            } catch (groovy.json.JsonException ex) {
                log.error("Problem parsing JSON: ${ex}.message")
                message = "Problem parsing JSON: ${ex}.message"
                status = 400
                return
            }
            def recGoldenDB = new JsonBuilder(slurped)
            log.debug("ALL: recoverySync is called giving my golden file DB: ${recGoldenDB.toString()}")
            message = recGoldenDB
            status = 200
        }
    }

    /*
    -----------------------------------------------------------------------------
    Execution: This execution is for the admin to distribute the security
    replication json file and plugins to all associated instances automatically
    making ease of use and bring up faster
    -----------------------------------------------------------------------------
    Usage: curl -X POST http://localhost:8081/artifactory/api/plugins/execute/distSecRep
    -----------------------------------------------------------------------------
    User: Admin
    -----------------------------------------------------------------------------
    Input: None
    -----------------------------------------------------------------------------
    Output: None
    -----------------------------------------------------------------------------
    TODO: Add in syncing the entire user DB on first run
    -----------------------------------------------------------------------------
    */
    distSecRep(httpMethod: 'POST') {
        def auth = org.artifactory.request.RequestThreadLocal.context.get().requestThreadLocal.request.getHeader("authorization")
        def targetFile = new File(artHome, '/plugins/securityReplication.json')
        if (!targetFile || !targetFile.exists()) {
            message = "Error: no securityReplication.json file found"
            status = 400
        } else {
            log.debug("Running distSecRep command")
            pushData(targetFile, auth)
        }
    }

    /*
    -----------------------------------------------------------------------------
    Execution: This execution is used for placing the security replication file
    into the user's artifactory instance
    -----------------------------------------------------------------------------
    Usage: curl -X PUT http://localhost:8081/artifactory/api/plugins/execute/securityReplication -T <textfile>
    -----------------------------------------------------------------------------
    User: Admin
    -----------------------------------------------------------------------------
    Input:
    securityReplication.json    -> this file is required to be specified by the
    admin so that the program can find out who are the associated instances that
    need to be replicated to
    -----------------------------------------------------------------------------
    Output: None
    -----------------------------------------------------------------------------
    */
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

    /*
    -----------------------------------------------------------------------------
    Execution: This execution is used for the the admin to get the list of
    artifactory instances associated that the admin wants te permissions to be
    replicated too
    -----------------------------------------------------------------------------
    Usage: curl -X GET http://localhost:8081/artifactory/api/plugins/execute/secRepList
    -----------------------------------------------------------------------------
    User: Admin
    -----------------------------------------------------------------------------
    Input: None
    -----------------------------------------------------------------------------
    Output: List of Artifactory URLs
    -----------------------------------------------------------------------------
    */
    secRepList(httpMethod: 'GET') {
        def inputFile = new File(artHome, '/plugins/securityReplication.json')
        def slurped = null
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
            message = new JsonBuilder(slurped.securityReplication.urls).toString()
            status = 200
        }
    }

    /*
    -----------------------------------------------------------------------------
    Execution: This execution is used to send the replication data between the
    master and slave instances, the data retrieve is for master to get the patch
    from the associated slaves and the data_send action is for the master to send
    the golden copy back to all the slaves, this function is not made for use by
    the admin but by the cron job automation
    -----------------------------------------------------------------------------
    Usage: curl -X GET http://localhost:8081/artifactory/api/plugins/execute/secRepDataGet?params=action=<action> -d <data>
    -----------------------------------------------------------------------------
    User: Master
    -----------------------------------------------------------------------------
    Input:
    param   -> This is the action to either send  back the diff patch or entir db diff
    data    -> The data is the string data of the slave's diff
    -----------------------------------------------------------------------------
    Output: None
    -----------------------------------------------------------------------------
    */
    secRepDataGet(httpMethod: 'GET') { params ->
        log.debug("SLAVE: secRepDataGet is called")
        def action = params?.('action')?.getAt(0) as String
        def slavePatch = null

        def secRepJson = new File(artHome, "/plugins/securityReplication.json")
        if (!secRepJson || !secRepJson.exists()){
            log.error("ALL: ${secRepJson} does not exist, nothing to do")
            return
        }

        def slurped = null
        try {
            slurped = new JsonSlurper().parseText(secRepJson.text)
        } catch (groovy.json.JsonException ex) {
            log.error("ALL: Problem parsing JSON: ${ex}.message")
            return
        }
        log.debug("SLAVE: secRepDataGet is called")

        def recovery = slurped.securityReplication.recovery

        if (recovery == true && memRecovery == false) {
            message = "[]"
            status = 200
        } else {
            def goldenFile = new File(artHome, '/plugins/goldenFile.json')
            if (!goldenFile || !goldenFile.exists()) {
                log.debug("SLAVE: no golden copy avaliable getting first slave extract")
                goldenDB = extract()
                writeToGoldenFile(goldenDB)
            } else {
                try {
                    goldenDB = new JsonSlurper().parseText(goldenFile.text)
                } catch (groovy.json.JsonException ex) {
                    log.error("Problem parsing JSON: ${ex}.message")
                    message = "Problem parsing JSON: ${ex}.message"
                    status = 400
                    return
                }
            }
            def nodeExtract = extract()

            log.debug("SLAVE: goldenDB is ${goldenDB.toString()}")
            log.debug("SLAVE: slaveExract is: ${nodeExtract.getClass()}, ${nodeExtract.toString()}")

            if (action == 'diff'){
                log.debug("SLAVE: secRepDataGet diff")
                slavePatch = buildDiff(goldenDB, nodeExtract)
            } else if (action == 'dbSync'){
                log.debug("SLAVE: secRepDataGet: dbSync")
                slavePatch = buildDiff(baseSnapShot, nodeExtract)
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
    }

    /*
    -----------------------------------------------------------------------------
    Execution: This executions use is to post data from the master to the slave
    -----------------------------------------------------------------------------
    Usage: curl -X POST http://localhost:8081/artifactory/api/plugins/execute/secRepDataPost -d <data>
    -----------------------------------------------------------------------------
    User: Master
    -----------------------------------------------------------------------------
    Input:
    data    -> The data is the json data of the master's golden diff back to
    slaves
    -----------------------------------------------------------------------------
    Output: None
    -----------------------------------------------------------------------------
    */
    secRepDataPost(httpMethod: 'POST') { ResourceStreamHandle body ->
        log.debug("SLAVE: secRepDataPost is called")
        def reader = new InputStreamReader(body.inputStream)
        def slurped = null
        try {
            slurped = new JsonSlurper().parse(reader)
        } catch (groovy.json.JsonException ex) {
            log.error("SLAVE: error parsing JSON: ${ex}.message")
            message = "SLAVE: Problem parsing JSON: ${ex}.message"
            status = 400
            return
        }
        log.debug("SLAVE: slurped: ${slurped.toString()}")

        def goldenFile = new File(artHome, '/plugins/goldenFile.json')
        if (!goldenFile || !goldenFile.exists()){
            log.debug("No goldenFile something is wrong")
            message = "No goldenFile something is wrong"
            status = 400
            return
        } else {
            try {
                goldenDB = new JsonSlurper().parseText(goldenFile.text)
            } catch (groovy.json.JsonException ex) {
                log.error("Problem parsing JSON: ${ex}.message")
                message = "Problem parsing JSON: ${ex}.message"
                status = 400
                return
            }
        }

        log.debug("SLAVE: original slave Golden is ${goldenDB.toString()}")

        def tempSlaveGolden = applyDiff(goldenDB, slurped)
        goldenDB = tempSlaveGolden
        log.debug("SLAVE: new slave Golden is ${goldenDB.toString()}")

        writeToGoldenFile(goldenDB)
        //insert into DB here
        updateDatabase(extract(), goldenDB)

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

//general artifactory cron job hook
jobs {

    /*
    -----------------------------------------------------------------------------
    Job: This security replication cron job is used by artifactory to figure out
    the master and either perform failover or aggragate and disperse data
    -----------------------------------------------------------------------------
    User: Master, Slave
    -----------------------------------------------------------------------------
    Input: None
    -----------------------------------------------------------------------------
    Output: None
    -----------------------------------------------------------------------------
    */
    securityReplicationWorker(cron: "*/30 * * * * ?") {
        MASTER = null
        DOWN_INSTANCE = []
        def masterPatch = null

        def goldenFile = new File(artHome, '/plugins/goldenFile.json')
        def targetFile = new File(artHome, "/plugins/securityReplication.json")

        if (!targetFile || !targetFile.exists()){
            log.error("ALL: ${targetFile} does not exist, nothing to do")
            return
        }

        def slurped = null
        try {
            slurped = new JsonSlurper().parseText(targetFile.text)
        } catch (groovy.json.JsonException ex) {
            log.error("ALL: Problem parsing JSON: ${ex}.message")
            return
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
        
        if (whoami != MASTER) {
            if (recovery == false){
                log.debug("SLAVE: This is my first time I'm coming up, telling master to sync everything for me")
                sleep(1000) //this sleep is here to avoid initial bring up timing colisions
                def resp = remoteCall(MASTER, auth, 'slaveDBSync', null)
                if (resp[1] != 200) {
                    throw new RuntimeException("SLAVE: slaveDBSync Failed: ${MASTER}: HTTP error code: " + resp[1])
                }
                //update securityReplication.json file recovery to true
                slurped.securityReplication.recovery = true
                JsonBuilder builder = new JsonBuilder(slurped)
                targetFile.withWriter{builder.writeTo(it)}
                log.debug("SLAVE: builder: ${builder.toString()}")
            } else if (recovery == true && memRecovery == false) {
                sleep(1000)
                log.debug("SLAVE: I am recovering from a failure, please send me the master golden db")
                def resp = remoteCall(MASTER, auth, 'recoverySync', null)
                if (resp[1] != 200) {
                    log.error("SLAVE: recoverySync failed: ${MASTER} retry(${retryCount}): HTTP error code: " + resp[1])
                    if (retryCount >= 3){
                        //update securityReplication.json file recovery to false
                        slurped.securityReplication.recovery = false
                        JsonBuilder builder = new JsonBuilder(slurped)
                        targetFile.withWriter{builder.writeTo(it)}
                        log.debug("SLAVE: builder: ${builder.toString()}")
                    } else {
                        retryCount = retryCount + 1
                    }
                    return
                }

                try {
                    goldenDB = new JsonSlurper().parseText(resp[0].toString())
                } catch (groovy.json.JsonException ex) {
                    log.error("Problem parsing JSON: ${ex}.message")
                }
                
                writeToGoldenFile(goldenDB)

                updateDatabase(null, goldenDB)
                memRecovery = true
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
                log.debug("MASTER: first time comming up, lets sync everything existing first")

                if (!goldenFile || !goldenFile.exists()){
                    log.error("No goldenFile lets get the first goldenDB")
                    goldenDB = extract()
                } else {
                    try {
                        goldenDB = new JsonSlurper().parseText(goldenFile.text)
                    } catch (groovy.json.JsonException ex) {
                        log.error("Problem parsing JSON: ${ex}.message")
                        return
                    }
                }
                
                writeToGoldenFile(goldenDB)

                log.debug("MASTER: goldenDB is: ${goldenDB.toString()}")

                log.debug("MASTER: baseSnapShot: ${baseSnapShot.getClass()}, ${baseSnapShot.toString()}")
                def dbMasterPatch = buildDiff(baseSnapShot, extract())
                log.debug("MASTER: dbMasterPatch: ${dbMasterPatch.getClass()}, ${dbMasterPatch.toString()}")

                log.debug("MASTER: going to slaves to grab everything")
                syncAll(distList, dbMasterPatch, whoami, auth, 'dbSync', slurped, targetFile)
            } else if (recovery == true && memRecovery == false){
                log.debug("MASTER: I am recovering from a failure, please next slave given me a new golden DB")
                def recoverySlave = null

                //find next slave who is not the master to get golden db from
                if (distList.size() > 1) {
                    for (i = 0; i < distList.size(); i++){
                        def instance = distList[i]
                        if (MASTER == instance && MASTER == whoami){
                            log.debug("MASTER: this is me, I need the recovery DB from the next up slave")
                        } else if (DOWN_INSTANCE.contains(instance)){
                            log.debug("MASTER: ${instance} is down, can't get anything from this slave")
                        } else {
                            def resp = remoteCall(instance, auth, 'ping', null)
                            if (resp[1] != 200){
                                log.error("MASTER: ping failed: ${MASTER}: HTTP error code: " + resp[1])
                            } else {
                                log.debug("MASTER: ${instance} is up, let's get the recovery DB from this slave")
                                recoverySlave = instance
                                break
                            }
                        }
                    }

                    def resp = remoteCall(recoverySlave, auth, 'recoverySync', null)
                    
                    if (resp[1] != 200) {
                        log.error("MASTER: recoverySync failed: ${MASTER}: retry(${retryCount}), HTTP error code: " + resp[1])
                        if (retryCount >= 3) {
                            //update securityReplication.json file recovery to false
                            slurped.securityReplication.recovery = false
                            JsonBuilder builder = new JsonBuilder(slurped)
                            targetFile.withWriter{builder.writeTo(it)}
                            log.debug("SLAVE: builder: ${builder.toString()}")
                        } else {
                            retryCount = retryCount + 1
                        }
                        return
                    }

                    try {
                        goldenDB = new JsonSlurper().parseText(resp[0].toString())
                    } catch (groovy.json.JsonException ex) {
                        log.error("Problem parsing JSON: ${ex}.message")
                    }

                    writeToGoldenFile(goldenDB)

                    updateDatabase(null, goldenDB)
                    memRecovery = true
                } else {
                    log.debug("MASTER: there is only me, i'll start from scratch")
                    goldenDB = extract()

                    writeToGoldenFile(goldenDB)
                    return
                }
            } else {
                log.debug("MASTER: Not first time coming up, lets do some updates")
                def nodeExtract = extract()
                log.debug("MASTER: nodeExtract is: ${nodeExtract.toString()}")
                masterPatch = buildDiff(goldenDB, nodeExtract)
                log.debug("MASTER: masterPatch is: ${masterPatch}")

                //set new goldenDB with current db snapshot
                goldenDB = nodeExtract
                log.debug("MASTER: new goldenDB is: ${goldenDB.toString()}")

                writeToGoldenFile(goldenDB)

                syncAll(distList, masterPatch, whoami, auth, 'diff', slurped, targetFile)
            }
        }
    }
}

def writeToGoldenFile(goldenDB){
    artHome = ctx.artifactoryHome.haAwareEtcDir
    def goldenFile = new File(artHome, '/plugins/goldenFile.json')
    log.debug("ALL: writing to goldenFile: ${goldenDB.toString()}")
    JsonBuilder builder = new JsonBuilder(goldenDB)
    goldenFile.withWriter{builder.writeTo(it)}
}

def readFromGoldenFile(){
    artHome = ctx.artifactoryHome.haAwareEtcDir
    def goldenFile = new File(artHome, '/plugins/goldenFile.json')
    def goldenDB = null
    if (!goldenFile || !goldenFile.exists()){
        log.error("No goldenFile something is wrong")
        return
    } else {
        try {
            goldenDB = new JsonSlurper().parseText(goldenFile.text)
        } catch (groovy.json.JsonException ex) {
            log.error("Problem parsing JSON: ${ex}.message")
            return 
        }
    }
    log.debug("ALL: reading from goldenFile: ${goldenDB.toString()}")
    return goldenDB
}

def syncAll(distList, patch, whoami, auth, action, slurped, jsonFile){
    log.debug("MASTER: Going to slaves to get stuff action: ${action}, patch:${patch.getClass()}")
    def bigDiff = grabStuffFromSlaves(distList, patch, whoami, auth, action)
    log.debug("MASTER: The aggragated diff is: ${bigDiff}")

    if (bigDiff.isEmpty()){
        log.debug("MASTER: The aggragated diff is empty, no need to do anything")
        if (action == 'dbSync') {
            log.debug("MASTER: The DB's are all empty nothing to sync on init")
            
            //update securityReplication.json file recovery to true
            slurped.securityReplication.recovery = true
            JsonBuilder builder = new JsonBuilder(slurped)
            jsonFile.withWriter{builder.writeTo(it)}
            log.debug("MASTER: builder: ${builder.toString()}")
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

        goldenDB = readFromGoldenFile()

        log.debug("MASTER: goldenDB: ${goldenDB.getClass()}, mergedPatch: ${mergedPatch.getClass()}")
        goldenDB = applyDiff(goldenDB, mergedPatch)
        log.debug("MASTER: the merged golden db is ${goldenDB.toString()}")
        
        //Apply the new merged master golden into the DB
        updateDatabase(extract(), goldenDB)
        if (action == 'dbSync'){
            //update securityReplication.json file recovery to true
            slurped.securityReplication.recovery = true
            JsonBuilder builder = new JsonBuilder(slurped)
            jsonFile.withWriter{builder.writeTo(it)}
            log.debug("MASTER: builder: ${builder.toString()}")
        }
    }
}

/*
-----------------------------------------------------------------------------
Fuction: This is a remote call to attach http requests on to a http client
given to it by the getHttpClint function
-The ping action will send a get request to see if the instance is active or
not
-The json action will send a http put request to place the
securityReplication.json file into the target instances plugin directory
-The plugin action will send a http put request to place the security and
replication plugin in to the instance list's plugin directory, this is used
mainly as a means of ease of distribution by the admin
-The data retrieve action is for the master to get the slave's patch to merge
onto the master patch
The data send action is for the master instance to send to the slave the master
patch to place into the slave's database
-----------------------------------------------------------------------------
User: Admin, Master
-----------------------------------------------------------------------------
Input:
baseurl -> this is the base url of the instance on the replication file list
auth    -> this is the auth for the admin to login to the remote instance
method  -> this is the type of action on the switch to be preformed
data    -> this is the data that is being sent, this can be null sometimes
-----------------------------------------------------------------------------
Output: This function will return the http response as a whole to be decifered
later on by the necessary functions such as status call or message content
-----------------------------------------------------------------------------
TODO: We may need to change the ease of use distribution later on to make it
more efficient or more protected
-----------------------------------------------------------------------------
*/
counter = 0
def remoteCall(baseurl, auth, method, data){
    counter = counter + 1
    log.debug("COUNTER COUNTER COUNTER: ${counter}")

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
            def url = "${baseurl}/api/plugins/execute/recoverySync"
            req = new HttpPost(url)
            req.addHeader("User-Agent", HttpUtils.artifactoryUserAgent)
            req.addHeader("Content-Type", "application/json")
            req.addHeader("Authorization", auth)
            break
        default: throw new RuntimeException("Invalid method ${method}.")
    }
    CloseableHttpResponse resp = null
    try {
        resp = httpclient.execute(req)
        retResp = getRespMessageString(resp)
        return retResp
    } catch (e) {
        return [null, 406]
    } finally {
        httpclient?.close()
        resp?.close()
    }
}

/*
-----------------------------------------------------------------------------
Fuction: This function is to push the data necessary to all remote instances
this is used mainly for ease of use for the admin so that he/she can just place
the plugin and replication file on one instance and it will be automatically
distributed to all associated instances
-----------------------------------------------------------------------------
User: Admin
-----------------------------------------------------------------------------
Input:
targetFile  -> this is the security replication file that will be sent to all
the associated instances (we make the assumption that in order to call this the
admin's security replication plugin is already installed)
auth        -> this is the authentication from the admin to access the remote
instances
-----------------------------------------------------------------------------
Output: None
-----------------------------------------------------------------------------
TODO: This may need to be revisited if it is wise to let users dynamically
provision so easily or if we want to spend the time to provision manually
-----------------------------------------------------------------------------
*/
def pushData(targetFile, auth) {
    def pluginFile = new File(artHome, '/plugins/securityReplication.groovy')
    def slurped = null
    log.debug("Running distSecRep command: inside pushData")
    try {
        slurped = new JsonSlurper().parseText(targetFile.text)
    } catch (groovy.json.JsonException ex) {
        message = "Problem parsing JSON: ${ex}.message"
        status = 400
        return
    }
    log.debug("Running distSecRep command: slurped: ${slurped.toString()} ")

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

/*
-----------------------------------------------------------------------------
Fuction: This function is used to send the golden copy of the master diff to
all the slaves
-----------------------------------------------------------------------------
User: Master
-----------------------------------------------------------------------------
Input:
distList    -> the distribution list of all the instances in the mesh topology
whoami      -> the instance that the master is running on from the json file
auth        -> the authorization of the master to access the instances (must be
in all instances
mergedPatch -> this is the master diff that the master will send to all slave
instances treated as an array to be converted to a string
-----------------------------------------------------------------------------
Output: Slave's HTTP response (currently the slave's golden copy but this will
change later)
-----------------------------------------------------------------------------
TODO: Change the slave's golden copy content response message to just a generic
response message (currently using the copy response for debugging purposes)
-----------------------------------------------------------------------------
*/
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

/*
-----------------------------------------------------------------------------
Fuction: Master will go to all the different slave instances on the list and
check if they are up or not given the system health ping
-----------------------------------------------------------------------------
User: Master
-----------------------------------------------------------------------------
Input:
distList  -> the distribution list of all the instances in the mesh topology
whoami    -> the instance that the master is running on from the json file
auth      -> the authorization of the master to access the instances (must be
in all instances
-----------------------------------------------------------------------------
Output: The output is that if the instances is up the master will get a status
ok which is 200 or if the instance is down the status will not be 200
-----------------------------------------------------------------------------
TODO: This function checks that the instances are up given the system health
ping but this instance may be up but does not have the plugin or the
instances plugin may not be working well so we need to change this to use
this plugins own system health check in the future
-----------------------------------------------------------------------------
*/
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

/*
-----------------------------------------------------------------------------
Fuction: This function is used to get the response message from the remote call
used by the http client
-----------------------------------------------------------------------------
User: Master
-----------------------------------------------------------------------------
Input: resp -> response message from the remote call
-----------------------------------------------------------------------------
Output: a string containing the message
-----------------------------------------------------------------------------
*/
def getRespMessageString(resp){
    InputStream ips = resp.getEntity().getContent()
    def statusCode = resp.getStatusLine().getStatusCode()
    return [ips.text, statusCode]
}

/*
-----------------------------------------------------------------------------
Fuction: This function is used to get all the diff's from the slaves to
aggragate
-----------------------------------------------------------------------------
User: Master
-----------------------------------------------------------------------------
Input:
distList  -> the distribution list of all the instances in the mesh topology
whoami    -> the instance that the master is running on from the json file
auth      -> the authorization of the master to access the instances (must be
in all instances
-----------------------------------------------------------------------------
Output: the bigDiff is returned as a giant array of all the patches to be merged
-----------------------------------------------------------------------------
TODO: We may need to revisit this will send stuff to slaves to find a better way
of using both functions instead of keeping them seperate
-----------------------------------------------------------------------------
*/
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
        DbUtils.close(rs)
    }
}

def extract() {
    def isolation = null, autocommit = null
    try {
        def jdbcHelper = ctx.beanForType(JdbcHelper)
        def conn = DataSourceUtils.doGetConnection(jdbcHelper.dataSource)
        isolation = conn.transactionIsolation
        autocommit = conn.autoCommit
        conn.transactionIsolation = Connection.TRANSACTION_SERIALIZABLE
        conn.autoCommit = false
        return databaseExtract()
    } finally {
        conn.autoCommit = autocommit
        conn.transactionIsolation = isolation
    }
}

def databaseExtract() {
    def jdbcHelper = ctx.beanForType(JdbcHelper)
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
    // extract from aces
    def useraces = [:]
    def groupaces = [:]
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
        group.permissions = groupaces[id] ?: [:]
        groupnames[id] = name
        groups[name] = group
    }
    // extract from users_groups
    def usergroups = HashMultimap.create()
    def usergroupquery = 'SELECT user_id, group_id FROM users_groups'
    select(jdbcHelper, usergroupquery) {
        usergroups.put(it.getLong(1), groupnames[it.getLong(2)])
    }
    // extract from user_props
    def userprops = [:]
    def userpropquery = 'SELECT user_id, prop_key, prop_value FROM user_props'
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
        user.groups = usergroups.get(id) ?: []
        user.properties = userprops[id] ?: [:]
        user.permissions = useraces[id] ?: [:]
        users[name] = user
    }
    return [permissions: perms, groups: groups, users: users]
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
                for (perm in val.permissions.entrySet()) {
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
                for (perm in val.permissions.entrySet()) {
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
    def isolation = null, autocommit = null
    try {
        def jdbcHelper = ctx.beanForType(JdbcHelper)
        def conn = DataSourceUtils.doGetConnection(jdbcHelper.dataSource)
        isolation = conn.transactionIsolation
        autocommit = conn.autoCommit
        conn.transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
        conn.autoCommit = false
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
        conn.commit()
    } catch (SQLException ex) {
        conn.rollback()
    } finally {
        conn?.autoCommit = autocommit
        conn?.transactionIsolation = isolation
    }
}

def getHash(json) {
    def digest = MessageDigest.getInstance("SHA-1")
    def hash = digest.digest(new JsonBuilder(normalize(json)).toString().bytes)
    return hash.encodeHex().toString()
}
