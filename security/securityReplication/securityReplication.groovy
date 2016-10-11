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

import org.apache.http.client.methods.*
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.HttpClients
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpResponseFactory;
import org.apache.http.HttpVersion;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.message.BasicStatusLine;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;

import org.artifactory.util.HttpClientConfigurator
import org.artifactory.util.HttpUtils

import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.transaction.support.TransactionSynchronizationManager

import org.artifactory.storage.db.util.DbUtils
import org.artifactory.storage.db.util.JdbcHelper
import org.artifactory.resource.ResourceStreamHandle
import static org.artifactory.repo.RepoPathFactory.create

import groovy.json.*


/* to enable logging ammend this to the end of artifactorys logback.xml
    <logger name="securityReplication">
        <level value="debug"/>
    </logger>
*/


//global variables 
httpclient = null
def slaveGolden = null
def masterGolden = null

/*
-----------------------------------------------------------------------------
Fuction: This is a generic http client creation from apache
-----------------------------------------------------------------------------
User: Admin, Master
-----------------------------------------------------------------------------
Input: None
-----------------------------------------------------------------------------
Output: httpclient -> this http client is used by the program to attach http
requests and information on to it
-----------------------------------------------------------------------------
TODO:
-----------------------------------------------------------------------------
*/
def getHttpClient() {
    if (!httpclient) {
        def builder = HttpClients.custom()
        builder.maxConnTotal = 50
        builder.maxConnPerRoute = 25
        httpclient = builder.build()
    }
    return httpclient
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
def remoteCall(baseurl, auth, method, data){
    switch(method) {
        case 'ping':
            def url = "${baseurl}/api/system/ping"
            log.debug("remote call url: ${url}")
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
            def url = "${baseurl}/api/plugins/execute/secRepData?params=action=${method}"
            req = new HttpPost(url)
            req.addHeader("User-Agent", HttpUtils.artifactoryUserAgent)
            req.addHeader("Content-Type", "application/json")
            req.addHeader("Authorization", auth)
            break
        case 'data_send':
            def url = "${baseurl}/api/plugins/execute/secRepData?params=action=${method}"
            req = new HttpPost(url)
            req.addHeader("User-Agent", HttpUtils.artifactoryUserAgent)
            req.addHeader("Content-Type", "text/plain")
            req.addHeader("Authorization", auth)
            if (data){
                req.entity = new StringEntity(data)
            }
            break
        default: throw new RuntimeException("Invalid method ${method}.")
    }

    try {
        HttpResponse resp = getHttpClient().execute(req)
        return resp
    } catch (e) {
        HttpResponseFactory factory = new DefaultHttpResponseFactory()
        HttpResponse resp = factory.newHttpResponse(new BasicStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_NOT_ACCEPTABLE, null), null);
        return resp
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
    def artHome = ctx.artifactoryHome.haAwareEtcDir
    def pluginFile = new File(artHome, '/plugins/securityReplication.groovy')
    def slurped = null
    try {
        slurped = new JsonSlurper().parseText(targetFile.text)
    } catch (groovy.json.JsonException ex) {
        message = "Problem parsing JSON: ${ex}.message"
        status = 400
        return
    }

    if (!pluginFile || !pluginFile.exists()) {
        log.error("GENERAL: ${pluginFile} not found")
        return
    }


    def distList = slurped.securityReplication.urls
    distList.each{
        if (slurped.securityReplication.whoami != it) {

            //check the health status of each artifactory instance
            def resp = remoteCall(it, auth, 'ping', null)
            if (resp.getStatusLine().getStatusCode() != 200) {
                throw new RuntimeException("Health Check Failed: ${it}: HTTP error code: " + resp.getStatusLine().getStatusCode())
            }

            slurped.securityReplication.whoami = "${it}"
            def builder = new JsonBuilder(slurped)

            //push plugin to all instances
            resp = remoteCall(it, auth, 'plugin', pluginFile.text)
            if (resp.getStatusLine().getStatusCode() != 200) {
                throw new RuntimeException("PLUGIN Push Failed: ${it}: HTTP error code: " + resp.getStatusLine().getStatusCode())
            }

            //push json to all instances 
            resp = remoteCall(it, auth, 'json', builder.toString())
            if (resp.getStatusLine().getStatusCode() != 200) {
                throw new RuntimeException("JSON Push Failed: ${it}: HTTP error code: " + resp.getStatusLine().getStatusCode())
            }
        }
    }
}

//general artifactory plugin execution hook
executions {
    def artHome = ctx.artifactoryHome.haAwareEtcDir

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
    TODO:
    -----------------------------------------------------------------------------
    */
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
    TODO:
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
    TODO:
    -----------------------------------------------------------------------------
    */
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

    /*
    -----------------------------------------------------------------------------
    Execution: This execution is used to send the replication data between the
    master and slave instances, the data retrieve is for master to get the patch
    from the associated slaves and the data_send action is for the master to send
    the golden copy back to all the slaves, this function is not made for use by
    the admin but by the cron job automation
    -----------------------------------------------------------------------------
    Usage: curl -X POST http://localhost:8081/artifactory/api/plugins/execute/secRepData?params=action=<action> -d <data>
    -----------------------------------------------------------------------------
    User: Master
    -----------------------------------------------------------------------------
    Input: 
    action -> the different types of action the master will perform
    -----------------------------------------------------------------------------
    Output: None
    -----------------------------------------------------------------------------
    TODO: This is still work in progress, the slave will need to handle the golden
    copy and input it into the database before saving it for the next time it needs
    to diff
    -----------------------------------------------------------------------------
    */
    secRepData(httpMethod: 'POST') { params, ResourceStreamHandle body ->
        def action = params?.('action')?.get(0) as String
        def reader = new InputStreamReader(body.inputStream, 'UTF-8')
        slurped = null

        switch (action) {
            case 'data_retrieve':
                if (slaveGolden == null || !slaveGolden) {
                    log.debug("SLAVE: no golden copy avaliable getting first slave extract")
                    slaveGolden = extract()
                } 
                def slaveExtract = extract()
                
                log.debug("SLAVE: slaveGolden is ${slaveGolden.toString()}")
                log.debug("SLAVE: slaveExract is: ${slaveExtract.toString()}")

                def slavePatch = buildDiff(slaveGolden, slaveExtract)
                log.debug("SLAVE: slavePatch is: ${slavePatch.toString()}")                

                //tempoarily set new slave golden extract until master gives new slave golden extract
                slaveGolden = slaveExtract
                
                //get diff from slave
                message = slavePatch
                status = 200
                break
            case 'data_send':
                try {
                    slurped = new JsonSlurper().parse(reader)
                } catch (groovy.json.JsonException ex) {
                    message = "SLAVE: Problem parsing JSON: ${ex}.message"
                    status = 400
                    return
                }
                //TODO insert into DB here
                log.debug("SLAVE: new diff is ${slurped.text}")
                log.debug("SLAVE: original slave Golden is ${slaveGolden.toString()}")
                slaveGolden = applyDiff(slaveGolden, slurped)
                log.debug("SLAVE: new slave Golden is ${slaveGolden.toString()}")
                
                message = slaveGolden.toString()
                status = 200
                break
            default: throw new RuntimeException("Invalide action ${action}")
        }
    }

    /*
    -----------------------------------------------------------------------------
    Execution:
    -----------------------------------------------------------------------------
    Usage:
    -----------------------------------------------------------------------------
    User:
    -----------------------------------------------------------------------------
    Input:
    -----------------------------------------------------------------------------
    Output:
    -----------------------------------------------------------------------------
    TODO:
    -----------------------------------------------------------------------------
    */
    testSecurityDump(httpMethod: 'GET') { params ->
        status = 200
        message = new JsonBuilder(normalize(extract())).toPrettyString()
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
    TODO:
    -----------------------------------------------------------------------------
    */
    securityReplicationWorker(cron: "*/30 * * * * ?") {
        MASTER = null
        DOWN_INSTANCE = []
        def masterPatch = null

        def artHome = ctx.artifactoryHome.haAwareEtcDir
        def targetFile = new File(artHome, "/plugins/securityReplication.json")

        if (!targetFile || !targetFile.exists()){
            log.error("ALL: ${targetFile} does not exist, nothing to do")
            return
        }

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
        def encoded = "$username:$password".getBytes().encodeBase64().toString()
        def auth = "Basic ${encoded}"    

        log.debug("ALL: whoami: ${whoami}")
        log.debug("ALL: Master: ${MASTER}")

        if (whoami == MASTER) {
            log.debug("MASTER: I am the Master")
        } else {
            log.debug("ALL: I don't know who I am, checking if Master is up")

            //check if master is up
            for (i = 0; i < distList.size(); i++) {
                def instance = distList[i]
                
                //set master
                if (instance == whoami) {
                    log.debug("MASTER: I am the Master setting Master")
                    MASTER = whoami

                    if (masterGolden == null || !masterGolden){
                        masterGolden = extract()
                    }
                    log.debug("MASTER: master golden is: ${masterGolden.toString()}")
                    def masterExtract = extract()
                    log.debug("MASTER: master extract is: ${masterExtract.toString()}")
                    
                    masterPatch = buildDiff(masterGolden, masterExtract)
                    log.debug("MASTER: master patch is: ${masterPatch}")
                
                    //set new masterGolden with current db snapshot
                    masterGolden = masterExtract
                    log.debug("MATER: new master Golden is: ${masterGolden.toString()}")
                    
                    break
                }

                log.debug("ALL: Checking if ${instance} is up")
                resp = remoteCall(instance, auth, 'ping', null)
                log.debug("ALL: ping statusCode: ${resp.getStatusLine().getStatusCode()}")
                if (resp.getStatusLine().getStatusCode() != 200) {
                    log.warn("ALL: ${instance} instance is down, finding new master\r")
                    //ammend to instance down list
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
            log.debug("SLAVE: I am a slave, going back to sleep")
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
            log.debug("MASTER: Going to slaves to get stuff")
            def bigDiff = grabStuffFromSlaves(distList, masterPatch, whoami, auth)
            log.debug("MASTER: The aggragated diff is: ${bigDiff}")

            def mergedPatch = merge(bigDiff)
            log.debug("MASTER: the merged golden patch is ${mergedPatch.toString()}")

            //TODO: send bigDiffFile back to slaves
            log.debug("MASTER: I gotta send the golden copy back to my slaves")
            sendSlavesGoldenCopy(distList, whoami, auth, mergedPatch)
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
distList  -> the distribution list of all the instances in the mesh topology
whoami    -> the instance that the master is running on from the json file
auth      -> the authorization of the master to access the instances (must be
in all instances
bigDiff   -> this is the master diff that the master will send to all slave
instances treated as an array to be converted to a string
-----------------------------------------------------------------------------
Output: Slave's HTTP response (currently the slave's golden copy but this will
change later)
-----------------------------------------------------------------------------
TODO: Change the slave's golden copy content response message to just a generic
response message (currently using the copy response for debugging purposes)
-----------------------------------------------------------------------------
*/
def sendSlavesGoldenCopy(distList, whoami, auth, bigDiff){
    //convert array to string
    def newBigDiff = bigDiff.join(", ")    
    log.debug("MASTER: Creating string with diff: ${newBigDiff}")

    for (i = 0; i < distList.size(); i++){
        def instance = distList[i]
        if ((MASTER == instance) && (MASTER == whoami)) {
            log.debug("MASTER: This is myself (the Master), I don't need to send stuff to myself")
        } else if (DOWN_INSTANCE.contains(instance)) {
            log.debug("MASTER: This instance: ${instance} is down, I don't need to give it stuff")
        } else {
            log.debug("MASTER: Sending bigDiff to ${instance}")
            resp = remoteCall(instance, auth, 'data_send', newBigDiff)
            if (resp.getStatusLine().getStatusCode() != 200) {
                log.error("MASTER: Error sending to ${instance} the bigDiff, statusCode: ${resp.getStatusLine().getStatusCode()}")
                break
            }
            response = getRespMessage(resp)
            log.debug("MASTER: Sent Data, Slave responds: ${response.toString()}")
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
        if ((instance == MASTER) && (whoami == MASTER)){
            log.debug("MASTER: This is myself (the Master), I'm up")
        } else {
            resp = remoteCall(instance, auth, 'ping', null)
            log.debug("MASTER: ping statusCode: ${resp.getStatusLine().getStatusCode()}")
            if (resp.getStatusLine().getStatusCode() != 200) {
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
TODO:
-----------------------------------------------------------------------------
*/
def getRespMessage(resp){
    InputStream ips = resp.getEntity().getContent()
    BufferedReader buf = new BufferedReader(new InputStreamReader(ips, "UTF-8"))
    StringBuilder sb = new StringBuilder()
    String s = null
    while (true){
        s = buf.readLine()
        if (s == null || s.size() == 0){
            break
        }
        sb.append(s)
    }
    buf.close()
    ips.close()
    return sb
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
def grabStuffFromSlaves(distList, masterDiff, whoami, auth){
    def bigDiff = []
    bigDiff << masterDiff

    for (i = 0; i < distList.size(); i++) {
        def instance = distList[i]
        if (MASTER == instance && MASTER == whoami){
            log.debug("MASTER: This is myself (the Master), don't need to do any work here")
        } else if (DOWN_INSTANCE.contains(instance)) {
            log.debug("MASTER: ${instance} is down, no need to do anything")
        } else {
            log.debug("MASTER: Accessing ${instance}, give me your stuff")
            resp = remoteCall(instance, auth, 'data_retrieve', null)
            if (resp.getStatusLine().getStatusCode() != 200) {
                log.error("MASTER: Error accessing ${instance}, failed to retrieve data")
                break
            }
        
        //get response message
        newDiff = getRespMessage(resp)
        log.debug("MASTER: Slaves stuff in newDiff: ${newDiff}")
        bigDiff << newDiff.toString()
        }
    }
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
//   - Permission: name [includes] [excludes] [repokeys]
//   - Group: name description default realm realmattrs [permission:privs]
//   - User: name password salt email passkey admin enabled updateable
//           privatekey publickey bintray locked expired
//           [group] [property] [permission:privs]

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
    def usergroups = HashMultimap.create() // user_id : [group_name]
    def usergroupquery = 'SELECT user_id, group_id FROM users_groups'
    select(jdbcHelper, usergroupquery) {
        usergroups.put(it.getLong(1), groupnames[it.getLong(2)])
    }
    // extract from user_props
    def userprops = [:] // user_id : [props]
    def userpropquery = 'SELECT user_id, prop_key, prop_value FROM user_props'
    select(jdbcHelper, userpropquery) {
        def userid = it.getLong(1)
        if (!(userid in userprops)) userprops[userid] = [:]
        userprops[userid][it.getString(2)] = it.getString(3)
    }
    // extract from users
    def users = [:] // user_id : ...
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

def buildDiff(oldver, newver) {
    def ptch = []
    diff([], ptch, normalize(oldver), normalize(newver))
    return ptch
}

def applyDiff(oldver, ptch) {
    return patch([], ptch.sort(diffsort), normalize(oldver))
}

def getHash(json) {
    def digest = MessageDigest.getInstance("SHA-1")
    def hash = digest.digest(new JsonBuilder(normalize(json)).toString().bytes)
    return hash.encodeHex().toString()
}
