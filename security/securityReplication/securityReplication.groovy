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

import org.artifactory.common.ArtifactoryHome
import org.artifactory.model.xstream.security.AceImpl
import org.artifactory.model.xstream.security.AclImpl
import org.artifactory.model.xstream.security.GroupImpl
import org.artifactory.model.xstream.security.PermissionTargetImpl
import org.artifactory.model.xstream.security.UserImpl
import org.artifactory.request.RequestThreadLocal
import org.artifactory.resource.ResourceStreamHandle
import org.artifactory.security.SaltedPassword
import org.artifactory.storage.db.DbService
import org.artifactory.storage.fs.service.ConfigsService
import org.artifactory.storage.db.servers.service.ArtifactoryServersCommonService
import org.artifactory.storage.db.util.DbUtils
import org.artifactory.storage.db.util.JdbcHelper
import org.artifactory.storage.security.service.AclStoreService
import org.artifactory.storage.security.service.UserGroupStoreService
import org.artifactory.util.HttpUtils

import org.jfrog.security.crypto.EncodedKeyPair
import org.jfrog.security.crypto.result.DecryptionStatusHolder

/* to enable logging append this to the end of artifactorys logback.xml
    <logger name="securityReplication">
        <level value="debug"/>
    </logger>
*/

//global variables
verbose = false
artHome = ctx.artifactoryHome.haAwareEtcDir

//general artifactory plugin execution hook
executions {
    //Usage: curl -X GET http://localhost:8081/artifactory/api/plugins/execute/getUserDB
    //External/Internal Use
    getUserDB(httpMethod: 'GET') {
        def (msg, stat) = getGoldenFile()
        message = unwrapData('js', msg)
        status = stat
    }

    //Usage: curl -X POST http://localhost:8081/artifactory/api/plugins/execute/distSecRep
    //External Use
    distSecRep(httpMethod: 'POST') {
        def (msg, stat) = pushData()
        message = unwrapData('js', msg)
        status = stat
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

    //Usage: curl -X GET http://localhost:8081/artifactory/api/plugins/execute/secRepPing
    //Internal Use
    secRepPing(httpMethod: 'GET') {
        log.debug("SLAVE: secRepPing is called")
        def (msg, stat) = getPingAndFingerprint()
        message = unwrapData('js', msg)
        status = stat
    }

    //Usage: curl -X POST http://localhost:8081/artifactory/api/plugins/execute/secRepDataGet -d <data>
    //Internal Use
    secRepDataGet(httpMethod: 'POST') { ResourceStreamHandle body ->
        def arg = wrapData('jo', null)
        log.debug("SLAVE: secRepDataGet is called")
        def bodytext = body.inputStream.text
        if (bodytext.length() > 0) {
            arg = wrapData('js', bodytext)
        }
        def (msg, stat) = getRecentPatch(arg)
        message = unwrapData('js', msg)
        status = stat
    }

    //Usage: curl -X POST http://localhost:8081/artifactory/api/plugins/execute/secRepDataPost -d <data>
    //Internal Use
    secRepDataPost(httpMethod: 'POST') { ResourceStreamHandle body ->
        log.debug("SLAVE: secRepDataPost is called")
        def arg = wrapData('ji', body.inputStream)
        def (msg, stat) = applyAggregatePatch(arg)
        message = unwrapData('js', msg)
        status = stat
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

    testSnapshotClear() {
        def cfgserv = ctx.beanForType(ConfigsService)
        cfgserv.deleteConfig("plugin.securityreplication.golden")
        cfgserv.deleteConfig("plugin.securityreplication.recent")
        cfgserv.deleteConfig("plugin.securityreplication.fingerprint")
        status = 200
        message = "Snapshots cleared successfully"
    }
}

def getCronJob() {
    def defaultcron = "0 0 0/1 * * ?"
    def slurped = null
    def jsonFile = new File(artHome, "/plugins/securityReplication.json")
    try {
        slurped = new JsonSlurper().parse(jsonFile)
    } catch (JsonException ex) {
        log.error("ALL: problem getting $jsonFile, using default")
        return defaultcron
    }
    def cron_job = slurped?.securityReplication?.cron_job
    if (cron_job) {
        log.debug("ALL: config cron job is being set at: $cron_job")
        return cron_job
    }  else {
        log.debug("ALL: cron job is not configured, using default")
        return defaultcron
    }
}

//general artifactory cron job hook
jobs {
    securityReplicationWorker(cron: getCronJob()) {
        def slurped = null
        def targetFile = new File(artHome, "/plugins/securityReplication.json")
        try {
            slurped = new JsonSlurper().parse(targetFile)
        } catch (JsonException ex) {
            log.error("ALL: problem getting $targetFile")
            return
        }
        def whoami = slurped.securityReplication.whoami
        def distList = slurped.securityReplication.urls
        def username = slurped.securityReplication.authorization.username
        def password = slurped.securityReplication.authorization.password
        def encoded = "$username:$password".getBytes().encodeBase64().toString()
        def auth = "Basic $encoded"
        def master = findMaster(distList, whoami, auth)
        log.debug("ALL: whoami: $whoami")
        log.debug("ALL: Master: $master")
        if (whoami != master) {
            log.debug("SLAVE: I am a slave, going back to sleep")
            return
        }
        if (distList.size() <= 1) {
            log.debug("MASTER: I'm all alone here, no need to do work")
            return
        }
        log.debug("MASTER: I am the Master, starting to do work")
        log.debug("MASTER: Checking my slave instances")
        def upList = checkSlaveInstances(distList, whoami, auth)
        log.debug("MASTER: upList size: ${upList.size()}, distList size: ${distList.size()}")
        if (2*upList.size() <= distList.size()) {
            log.debug("MASTER: Cannot continue, majority of instances unavailable")
            return
        }
        log.debug("MASTER: Available instances are: $upList")
        log.debug("MASTER: Let's do some updates")
        log.debug("MASTER: Getting the golden file")
        def golden = findBestGolden(upList, whoami, auth)
        log.debug("MASTER: Going to slaves to get stuff")
        def bigDiff = grabStuffFromSlaves(golden, upList, whoami, auth)
        if (verbose == true) {
            log.debug("MASTER: The aggragated diff is: $bigDiff")
        }
        def mergedPatch = merge(bigDiff)
        if (verbose == true) {
            log.debug("MASTER: the merged golden patch is $mergedPatch")
        }
        log.debug("MASTER: I gotta send the golden copy back to my slaves")
        sendSlavesGoldenCopy(upList, whoami, auth, mergedPatch, golden)
    }
}

def readFile(fname) {
    def cfgserv = ctx.beanForType(ConfigsService)
    def data = cfgserv.getStreamConfig("plugin.securityreplication.$fname")
    if (!data) return null
    return wrapData('ji', data)
}

def writeFile(fname, content) {
    def data = unwrapData('js', content)
    def cfgserv = ctx.beanForType(ConfigsService)
    try {
        cfgserv.addOrUpdateConfig("plugin.securityreplication.$fname", data)
    } catch (MissingMethodException ex) {
        def t = System.currentTimeMillis()
        cfgserv.addOrUpdateConfig("plugin.securityreplication.$fname", data, t)
    }
}

def remoteCall(whoami, baseurl, auth, method, data = wrapData('jo', null)) {
    def exurl = "$baseurl/api/plugins/execute"
    def me = whoami == baseurl
    switch(method) {
        case 'json':
            def req = new HttpPut("$exurl/securityReplication")
            return makeRequest(req, auth, data, "text/plain")
        case 'plugin':
            def req = new HttpPut("$baseurl/api/plugins/securityReplication")
            return makeRequest(req, auth, data, "text/plain")
        case 'data_send':
            if (me) return applyAggregatePatch(wrapData('js', unwrapData('js', data)))
            def req = new HttpPost("$exurl/secRepDataPost")
            return makeRequest(req, auth, data, "application/json")
        case 'data_retrieve':
            if (me) return getRecentPatch(data)
            def req = new HttpPost("$exurl/secRepDataGet")
            return makeRequest(req, auth, data, "application/json")
        case 'recoverySync':
            if (me) return getGoldenFile()
            def req = new HttpGet("$exurl/getUserDB")
            return makeRequest(req, auth)
        case 'ping':
            if (me) return getPingAndFingerprint()
            def req = new HttpGet("$exurl/secRepPing")
            return makeRequest(req, auth)
        default: throw new RuntimeException("Invalid method $method")
    }
}

def makeRequest(req, auth, data = null, ctype = null) {
    def resp = null, httpclient = HttpClients.createDefault()
    req.addHeader("User-Agent", HttpUtils.artifactoryUserAgent)
    req.addHeader("Authorization", auth)
    if (data) {
        req.addHeader("Content-Type", ctype)
        req.entity = new StringEntity(unwrapData('js', data))
    }
    try {
        resp = httpclient.execute(req)
        def ips = resp.entity.content
        def statusCode = resp.statusLine.statusCode
        return [wrapData('js', ips.text), statusCode]
    } catch (ex) {
        return [wrapData('js', "Problem making request: $ex.message"), 502]
    } finally {
        httpclient?.close()
        resp?.close()
    }
}

def pushData() {
    def targetFile = new File(artHome, '/plugins/securityReplication.json')
    def slurped = null
    try {
        slurped = new JsonSlurper().parse(targetFile)
    } catch (JsonException ex) {
        return [wrapData('js', "Problem parsing JSON: $ex.message"), 400]
    }
    log.debug("Running distSecRep command: slurped: $slurped")
    def whoami = slurped.securityReplication.whoami
    def username = slurped.securityReplication.authorization.username
    def password = slurped.securityReplication.authorization.password
    def encoded = "$username:$password".getBytes().encodeBase64().toString()
    def auth = "Basic $encoded"
    def pluginFile = new File(artHome, '/plugins/securityReplication.groovy')
    log.debug("Running distSecRep command: inside pushData")
    if (!pluginFile?.exists()) return [wrapData('js', "$pluginFile not found"), 400]
    def distList = slurped.securityReplication.urls
    log.debug("Running distSecRep command: distList: $distList")
    def pluginData = wrapData('js', pluginFile.text)
    for (dist in distList) {
        if (whoami == dist) continue
        slurped.securityReplication.whoami = "$dist"
        //push plugin to all instances
        log.debug("sending plugin to $dist instance")
        resp = remoteCall(whoami, dist, auth, 'plugin', pluginData)
        if (resp[1] != 200) {
            return [wrapData('js', "PLUGIN Push $dist Failed: ${resp[0][2]}"), resp[1]]
        }
        //push json to all instances
        log.debug("sending json file to $dist instance")
        resp = remoteCall(whoami, dist, auth, 'json', wrapData('jo', slurped))
        if (resp[1] != 200) {
            return [wrapData('js', "JSON Push $dist Failed: ${resp[0][2]}"), resp[1]]
        }
    }
    return [wrapData('js', "Pushed all data successfully"), 200]
}

def findBestGolden(upList, whoami, auth) {
    def baseSnapShot = ["users":[:],"groups":[:],"permissions":[:]]
    def synched = upList.every { k, v -> v?.cs == upList[whoami]?.cs }
    if (synched) return [fingerprint: upList[whoami], golden: baseSnapShot]
    def latest = null, golden = null
    for (inst in upList.entrySet()) {
        if (!inst.value) continue
        if (!latest || latest.value.ts < inst.value.ts) latest = inst
    }
    if (!latest) return [fingerprint: null, golden: baseSnapShot]
    if (latest.value.cs == upList[whoami].cs) golden = whoami
    else golden = latest.key
    def resp = remoteCall(whoami, golden, auth, 'recoverySync')
    if (resp[1] != 200) {
        throw new RuntimeException("failed to retrieve golden from $golden")
    }
    return [fingerprint: latest.value, golden: unwrapData('jo', resp[0])]
}

def sendSlavesGoldenCopy(upList, whoami, auth, mergedPatch, golden) {
    def fingerprint = [cs: null, ts: System.currentTimeMillis()]
    if (golden.fingerprint && fingerprint.ts <= golden.fingerprint.ts) {
        fingerprint.ts = 1 + golden.fingerprint.ts
    }
    for (instance in upList.entrySet()) {
        log.debug("MASTER: Sending mergedPatch to $instance.key")
        def data = wrapData('jo', [fingerprint: fingerprint, patch: mergedPatch])
        def resp = remoteCall(whoami, instance.key, auth, 'data_send', data)
        if (resp[1] != 200) {
            log.error("MASTER: Error sending to $instance.key the mergedPatch, statusCode: ${resp[1]}")
        }
        def newfinger = unwrapData('jo', resp[0])
        if (verbose == true) {
            log.debug("MASTER: Sent Data, Slave responds: $newfinger")
        }
        if (fingerprint.cs == null) fingerprint.cs = newfinger.cs
        if (fingerprint != newfinger) {
            log.error("MASTER: Response from $instance indicates bad sync (fingerprint mismatch)")
        }
    }
}

def findMaster(distList, whoami, auth) {
    log.debug("ALL: I don't know who I am, checking if Master is up")
    for (instance in distList) {
        log.debug("ALL: Checking if $instance is up")
        def resp = remoteCall(whoami, instance, auth, 'ping')
        log.debug("ALL: ping statusCode: ${resp[1]}")
        if (resp[1] != 200) {
            log.warn("ALL: $instance instance is down, finding new master\r")
        } else {
            log.debug("ALL: $instance is up, setting Master \r")
            return instance
        }
    }
    def msg = "Cannot find master. Please check the configuration file and"
    msg += " ensure that $whoami is in the urls list."
    throw new RuntimeException(msg)
}

def checkSlaveInstances(distList, whoami, auth) {
    def upList = [:]
    for (instance in distList) {
        def resp = remoteCall(whoami, instance, auth, 'ping')
        log.debug("MASTER: ping statusCode: ${resp[1]}")
        if (resp[1] != 200) {
            log.warn("MASTER: Slave: $instance instance is down")
        } else {
            log.debug("MASTER: Slave: $instance instance is up")
            try {
                upList[instance] = unwrapData('jo', resp[0])
            } catch (Exception ex) {
                upList[instance] = null
            }
        }
    }
    return upList
}

def grabStuffFromSlaves(golden, upList, whoami, auth) {
    def bigDiff = []
    for (inst in upList.entrySet()) {
        log.debug("MASTER: Accessing $inst.key, give me your stuff")
        def resp = null
        def data = wrapData('jo', golden)
        if (golden.fingerprint && golden.fingerprint.cs == inst.value?.cs) {
            resp = remoteCall(whoami, inst.key, auth, 'data_retrieve')
        } else {
            resp = remoteCall(whoami, inst.key, auth, 'data_retrieve', data)
        }
        if (resp[1] != 200) {
            throw new RuntimeException("failed to retrieve data from $inst.key")
        }
        def newDiff = unwrapData('jo', resp[0])
        if (verbose == true) {
            log.debug("MASTER: Slaves stuff in newDiff: $newDiff")
        }
        bigDiff << newDiff
    }
    if (verbose == true) {
        log.debug("MASTER: slave bigdiff return: $bigDiff")
    }
    return bigDiff
}

// Sometimes we generate json objects, other times we pull json strings from the
// database, other times we need to look inside a json object or write a string
// to the database. Sometimes we need to convert to or from a string to send
// over a network, and sometimes we don't. All of these systems interacting in
// various ways would require either many different special case handling
// scenarios, or else some unnecessary and potentially costly conversions
// between json object and string. This wrapper system allows us to avoid both
// of those issues. It works like so:
// - When a value is created, wrap it. This saves the value with its type, and
//   they will be passed around together wherever the value goes.
// - When you need to use a value, unwrap it. This converts the value to the
//   required type if necessary.
// Supported types are 'ji' (json input stream), 'js' (json string), and 'jo'
// (json object).

def wrapData(typ, data) {
    if (data && data instanceof Iterable && data[0] == 'datawrapper') {
        throw new RuntimeException("Cannot wrap wrapped data: $data")
    }
    if (!(typ in ['ji', 'js', 'jo'])) {
        throw new RuntimeException("Data type $typ not recognized")
    }
    return ['datawrapper', typ, data]
}

def unwrapData(rtyp, wdata) {
    if (!wdata || !(wdata instanceof Iterable) || wdata[0] != 'datawrapper') {
        throw new RuntimeException("Cannot unwrap non-wrapped data: $wdata")
    }
    if (!(rtyp in ['ji', 'js', 'jo'])) {
        throw new RuntimeException("Data type $rtyp not recognized")
    }
    def (kw, typ, data) = wdata
    if (!(typ in ['ji', 'js', 'jo'])) {
        throw new RuntimeException("Data type $typ not recognized")
    }
    if (typ == rtyp) return data
    if (typ == 'js' && rtyp == 'jo') {
        return new JsonSlurper().parseText(data)
    } else if (typ == 'jo' && rtyp == 'js') {
        return new JsonBuilder(data).toString()
    } else if (typ == 'ji' && rtyp == 'js') {
        return data.text
    } else if (typ == 'ji' && rtyp == 'jo') {
        return new JsonSlurper().parse(data)
    } else {
        throw new RuntimeException("Cannot convert $typ to $rtyp")
    }
}

// Each of the following functions contains the logic for one of the execution
// hooks. The idea is that the master is going to want to call the same code on
// itself as on any of the other instances, so to avoid code duplication, we
// separate the implementations out here. When the Master makes a call to any
// execution hook, there is intermediate code that checks if it's calling it on
// itself, and if so, it runs one of these functions directly instead of making
// a REST call. This way we can avoid making HTTP requests to ourselves without
// resorting to more obtuse logic.

def getGoldenFile() {
    def is = null
    try {
        is = readFile('golden')
        if (is) return [wrapData('js', unwrapData('js', is)), 200]
        else return [wrapData('js', "The golden user file does not exist"), 400]
    } finally {
        if (is) unwrapData('ji', is).close()
    }
}

def getPingAndFingerprint() {
    def is = null
    try {
        is = readFile('fingerprint')
        if (is) return [wrapData('js', unwrapData('js', is)), 200]
        else return [wrapData('jo', null), 200]
    } finally {
        if (is) unwrapData('ji', is).close()
    }
}

def getRecentPatch(newgolden) {
    def baseSnapShot = ["users":[:],"groups":[:],"permissions":[:]]
    def newgoldenuw = unwrapData('jo', newgolden)
    def goldenDB = newgoldenuw?.golden
    def extracted = extract()
    def is = null
    if (!newgoldenuw) {
        try {
            is = readFile('golden')
            if (!is) throw new JsonException("Golden file not found.")
            goldenDB = unwrapData('jo', is)
        } catch (JsonException ex) {
            log.error("Problem parsing JSON: $ex.message")
            return [wrapData('js', "Problem parsing JSON: $ex.message"), 400]
        } finally {
            if (is) unwrapData('ji', is).close()
        }
    } else {
        def goldendiff = buildDiff(baseSnapShot, goldenDB)
        def extractdiff = buildDiff(baseSnapShot, extracted)
        def mergeddiff = merge([goldendiff, extractdiff])
        extracted = applyDiff(baseSnapShot, mergeddiff)
        updateDatabase(null, extracted)
        writeFile('fingerprint', wrapData('jo', newgoldenuw.fingerprint))
        writeFile('golden', wrapData('jo', goldenDB))
    }
    if (verbose == true) {
        log.debug("SLAVE: goldenDB is $goldenDB")
    }
    writeFile('recent', wrapData('jo', extracted))
    if (verbose == true) {
        log.debug("SLAVE: slaveExract is: $extracted")
    }
    log.debug("SLAVE: secRepDataGet diff")
    def slavePatch = buildDiff(goldenDB, extracted)
    if (verbose == true) {
        log.debug("SLAVE: slavePatch is: $slavePatch")
    }
    return [wrapData('jo', slavePatch), 200]
}

def applyAggregatePatch(newpatch) {
    def goldenDB = null, oldGoldenDB = null
    def patch = unwrapData('jo', newpatch)
    if (verbose == true) {
        log.debug("SLAVE: newpatch: $patch")
    }
    def is = null
    try {
        is = readFile('golden')
        if (!is) throw new JsonException("Golden file not found.")
        oldGoldenDB = unwrapData('jo', is)
    } catch (JsonException ex) {
        log.error("Problem parsing JSON: $ex.message")
        return [wrapData('js', "Problem parsing JSON: $ex.message"), 400]
    } finally {
        if (is) unwrapData('ji', is).close()
    }
    if (verbose == true) {
        log.debug("SLAVE: original slave Golden is $oldGoldenDB")
    }
    goldenDB = applyDiff(oldGoldenDB, patch.patch)
    if (verbose == true) {
        log.debug("SLAVE: new slave Golden is $goldenDB")
    }
    patch.fingerprint.cs = getHash(goldenDB)
    writeFile('fingerprint', wrapData('jo', patch.fingerprint))
    writeFile('golden', wrapData('jo', goldenDB))
    is = null
    try {
        is = readFile('recent')
        if (!is) throw new JsonException("Recent file not found.")
        def extracted = unwrapData('jo', is)
        updateDatabase(extracted, goldenDB)
    } catch (JsonException ex) {
        log.error("Could not parse recent.json: $ex.message")
        return [wrapData('js', "Could not parse recent.json: $ex.message"), 400]
    } finally {
        if (is) unwrapData('ji', is).close()
    }
    return [wrapData('jo', patch.fingerprint), 200]
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

def mastercrypt(json, encrypt) {
    def home = null, cryptoHelper = null
    try {
        def art4ch = "org.artifactory.security.crypto.CryptoHelper"
        cryptoHelper = Class.forName(art4ch)
        if (!cryptoHelper.hasMasterKey()) return
    } catch (ClassNotFoundException ex) {
        home = ArtifactoryHome.get()
        def art5ch = "org.artifactory.common.crypto.CryptoHelper"
        cryptoHelper = Class.forName(art5ch)
        if (!cryptoHelper.hasMasterKey(home)) return
    }
    def encprops = ['basictoken', 'ssh.basictoken', 'sumologic.access.token',
                    'sumologic.refresh.token', 'docker.basictoken', 'apiKey']
    def masterWrapper = ArtifactoryHome.get().masterEncryptionWrapper
    def wrapper = encrypt ? masterWrapper : null
    for (user in json.users.values()) {
        if (user.privatekey && user.publickey) {
            def pair = new EncodedKeyPair(user.privatekey, user.publickey)
            try {
                pair = pair.decode(masterWrapper, new DecryptionStatusHolder())
            } catch (MissingMethodException ex) {
                pair = pair.decode(masterWrapper)
            }
            pair = new EncodedKeyPair(pair, wrapper)
            user.privatekey = pair.encodedPrivateKey
            user.publickey = pair.encodedPublicKey
        }
        user.properties.each { k, v ->
            if (!(k in encprops)) return
            if (home) {
                if (encrypt) {
                    user.properties[k] = cryptoHelper.encryptIfNeeded(home, v)
                } else {
                    user.properties[k] = cryptoHelper.decryptIfNeeded(home, v)
                }
            } else {
                if (encrypt) {
                    user.properties[k] = cryptoHelper.encryptIfNeeded(v)
                } else {
                    user.properties[k] = cryptoHelper.decryptIfNeeded(v)
                }
            }
        }
    }
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
        filter = slurped?.securityReplication?.filter ?: 1
    } catch (JsonException ex) {
        filter = 1
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
    mastercrypt(result, false)
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
    mastercrypt(currver, true)
    mastercrypt(truever, true)
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
