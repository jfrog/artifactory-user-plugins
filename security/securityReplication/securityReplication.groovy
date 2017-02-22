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
import groovy.json.JsonException
import groovy.json.JsonSlurper

import java.security.MessageDigest

import org.apache.http.client.methods.*
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.HttpClients

import org.artifactory.common.ArtifactoryHome
import org.artifactory.factory.InfoFactoryHolder
import org.artifactory.resource.ResourceStreamHandle
import org.artifactory.security.SaltedPassword
import org.artifactory.storage.fs.service.ConfigsService
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
    if (latest.value.cs == upList[whoami]?.cs) golden = whoami
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

// Security snapshot JSON format:
// Snapshots of the security data are taken and stored in a JSON format, to
// allow for easy handling between Artifactory instances:
// {
//   "users": {
//     "<user name>": {
//       "password": <string>,
//       "salt": <string>,
//       "email": <string>,
//       "passkey": <string>,
//       "admin": <boolean>,
//       "enabled": <boolean>,
//       "updatable": <boolean>,
//       "privatekey": <string>,
//       "publickey": <string>,
//       "bintray": <string>,
//       "locked": <boolean>,
//       "expired": <boolean>,
//       "groups": [
//         "<group name>",
//         "<another group name>",
//         ...
//       ],
//       "properties": {
//         "<property name>": <string>,
//         "<another property name>": <string>,
//         ...
//       },
//       "permissions": {
//         "<permission name>": <dmnrw string>,
//         "<another permission name>": <dmnrw string>,
//         ...
//       }
//     },
//     "<another user name>": {
//       ...
//     },
//     ...
//   },
//   "groups": {
//     "<group name>": {
//       "description": <string>,
//       "isdefault": <boolean>,
//       "realm": <string>,
//       "realmattrs": <string>,
//       "permissions": {
//         "<permission name>": <dmnrw string>,
//         "<another permission name>": <dmnrw string>,
//         ...
//       }
//     },
//     "<another group name>": {
//       ...
//     },
//     ...
//   },
//   "permissions": {
//     "<permission name>": {
//       "includes": [
//         "<include pattern>",
//         "<another include pattern>",
//         ...
//       ],
//       "excludes": [
//         "<exclude pattern>",
//         "<another exclude pattern>",
//         ...
//       ],
//       "repos": [
//         "<repository name>",
//         "<another repository name>",
//         ...
//       ]
//     },
//     "<another permission name>": {
//       ...
//     },
//     ...
//   }
// }

// JSON diff format:
// To track changes between snapshots, we use a JSON diff/patch system. This
// system compares JSON objects, and outputs patches in a JSON format:
// - A patch consists of a list of changes
// - Each change is a tuple (list) with one of the following formats:
//   [<path>, <op>, <key>, <val>]
//   [<path>, <op>, <key>]
//   [<path>, <op>, <val>]
// - <path> is a list of strings representing a path to the object to change.
//   Each string is a key in a JSON map. For example:
//   {"users": {"foo": {"email": "f@bar.baz", ...}, ...}, "groups": {...}, ...}
//   To access the email field, the path is ["users", "foo", "email"]
// - <key> is a string representing the key to update in the map being changed.
//   <val> is the new value to use.
// - <op> is the operation to run on the specified map. Depending on this value,
//   either <key>, <val>, or both might be required. <op> is one of the
//   following:
//   - ":~" updates the specified simple value (string, number, or boolean) in a
//     map. This only takes <val>, which is the new value to set.
//   - ":+" adds a simple value to the specified list. This only takes <val>,
//     which is the new value to add.
//   - ":-" removes a simple value from the specified list. This only takes
//     <val>, which is the value to remove.
//   - ";+" adds a new key/value pair to the specified map. This takes both
//     <key> and <val>, which are the new pair to add.
//   - ";-" removes a key/value pair from the specified map. This only takes
//     <key>, which is the key of the pair to remove.
// The format is limited in the following ways:
// - Lists should not contain complex data such as other lists or maps, as they
//   don't diff well: The diff system treats list elements as atomic values and
//   does not descend into them.
// - Lists should not depend on element ordering, as the diff system does not
//   have a way to track or specify the order of elements in a list.
// This is okay in this case, because every list in the snapshot format is an
// unordered set of strings.

diffsort = { left, right ->
    def ls = left[0].size(), rs = right[0].size()
    for (i in 0..([ls, rs].min())) {
        if (left[0][i] != right[0][i]) return left[0][i] <=> right[0][i]
    }
    if (ls != rs) return ls <=> rs
    return right[1] <=> left[1]
}

writesort = { left, right ->
    def opord = [';+': 0, ':+': 1, ':~': 2, ':-': 3, ';-': 4]
    def typord = ['permissions': 0, 'groups': 1, 'users': 2]
    if (left[1] != right[1]) return opord[left[1]] <=> opord[right[1]]
    if (left[0][0] != right[0][0]) {
        def result = typord[left[0][0]] <=> typord[right[0][0]]
        return left[1] == ';-' ? -result : result
    }
    if (left[1] == ':~' && left[0][0] == 'users') {
        if (left[0][2] == 'expired' && right[0][2] != 'expired') return 1
        if (right[0][2] == 'expired' && left[0][2] != 'expired') return -1
    }
    return diffsort(left, right)
}

def encryptDecrypt(json, encrypt) {
    def home = ArtifactoryHome.get()
    def is5x = false, cryptoHelper = null
    try {
        def art4ch = "org.artifactory.security.crypto.CryptoHelper"
        cryptoHelper = Class.forName(art4ch)
        if (!cryptoHelper.hasMasterKey()) return
    } catch (ClassNotFoundException ex) {
        is5x = true
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
            if (is5x && encrypt) {
                user.properties[k] = cryptoHelper.encryptIfNeeded(home, v)
            } else if (is5x) {
                user.properties[k] = cryptoHelper.decryptIfNeeded(home, v)
            } else if (encrypt) {
                user.properties[k] = cryptoHelper.encryptIfNeeded(v)
            } else {
                user.properties[k] = cryptoHelper.decryptIfNeeded(v)
            }
        }
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

def readMask(privs) {
    def mask = ''
    if (privs &  8) mask += 'd'
    if (privs & 16) mask += 'm'
    if (privs &  4) mask += 'n'
    if (privs &  1) mask += 'r'
    if (privs &  2) mask += 'w'
    return mask
}

def extract() {
    def secserv = ctx.securityService
    def result = [:]
    // get the filter settings from the config file
    def filter = null
    def secRepJson = new File(artHome, '/plugins/securityReplication.json')
    try {
        def slurped = new JsonSlurper().parse(secRepJson)
        filter = slurped?.securityReplication?.filter ?: 1
    } catch (JsonException ex) {
        filter = 1
    }
    // permissions
    if (filter >= 3) {
        def perms = [:], acls = secserv.allAcls
        for (acl in acls) {
            def perm = [:]
            perm.includes = acl.permissionTarget.includes
            perm.excludes = acl.permissionTarget.excludes
            perm.repos = acl.permissionTarget.repoKeys
            perms[acl.permissionTarget.name] = perm
        }
        result['permissions'] = perms
    }
    // groups
    if (filter >= 2) {
        def groups = [:], grps = secserv.allGroups
        for (grp in grps) {
            def group = [:]
            group.description = grp.description
            group.isdefault = grp.newUserDefault
            group.realm = grp.realm
            group.realmattrs = grp.realmAttributes
            if (filter >= 3) {
                def perms = secserv.getGroupsPermissions([grp.groupName])
                try {
                    group.permissions = perms.asMap().collectEntries { k, vs ->
                        [k.name, readMask(vs[0].mask)]
                    }
                } catch (MissingMethodException ex) {
                    group.permissions = perms.collectEntries { k, v ->
                        [k.name, readMask(v.mask)]
                    }
                }
            }
            groups[grp.groupName] = group
        }
        result['groups'] = groups
    }
    // users
    if (filter >= 1) {
        def users = [:], usrs = secserv.getAllUsers(true)
        for (usr in usrs) {
            def user = [:]
            user.password = usr.password
            user.salt = usr.salt
            user.email = usr.email
            user.passkey = usr.genPasswordKey
            user.admin = usr.isAdmin()
            user.enabled = usr.isEnabled()
            user.updatable = usr.isUpdatableProfile()
            user.privatekey = usr.privateKey
            user.publickey = usr.publicKey
            user.bintray = usr.bintrayAuth
            user.locked = usr.isLocked()
            user.expired = usr.isCredentialsExpired()
            user.properties = usr.userProperties.collectEntries {
                [it.propKey, it.propValue]
            }
            user.properties = user.properties.findAll { k, v ->
                k != 'passwordCreated'
            }
            if (filter >= 2) user.groups = usr.groups.collect { it.groupName }
            if (filter >= 3) {
                def perms = secserv.getUserPermissions(usr.username)
                user.permissions = perms.collectEntries { k, v ->
                    [k.name, readMask(v.mask)]
                }
            }
            users[usr.username] = user
        }
        result['users'] = users
    }
    encryptDecrypt(result, false)
    return result
}

def modifyProp(secserv, op, user, key, val) {
    def encprops = ['basictoken', 'ssh.basictoken', 'sumologic.access.token',
                    'sumologic.refresh.token', 'docker.basictoken', 'apiKey']
    if (key in encprops) {
        if (op == 'create') {
            secserv.createPropsToken(user, key, val)
        } else if (op == 'delete') {
            secserv.revokePropsToken(user, key)
        } else if (op == 'update') {
            secserv.updatePropsToken(user, key, val)
        }
    } else {
        if (op == 'create') {
            secserv.addUserProperty(user, key, val)
        } else if (op == 'delete') {
            secserv.deleteProperty(user, key)
        } else if (op == 'update') {
            if (secserv.deleteProperty(user, key)) {
                secserv.addUserProperty(user, key, val)
            }
        }
    }
}

def update(ptch) {
    def secserv = ctx.securityService
    def infact = InfoFactoryHolder.get()
    for (line in ptch) {
        def (path, oper, key, val) = line
        def pathsize = path.size()
        if (pathsize == 3 && oper in [':+', ':-'] &&
            path[0] == 'permissions' &&
            path[2] in ['includes', 'excludes', 'repos']) {
            // permission targets
            def acl = infact.copyAcl(secserv.getAcl(path[1]))
            def perm = infact.copyPermissionTarget(acl.permissionTarget)
            def op = null
            if (oper == ':+') op = { coll, elem -> coll + elem }
            else if (oper == ':-') op = { coll, elem -> coll - elem }
            if (path[2] == 'includes') {
                perm.includes = op(perm.includes, key)
            } else if (path[2] == 'excludes') {
                perm.excludes = op(perm.excludes, key)
            } else if (path[2] == 'repos') {
                perm.repoKeys = op(perm.repoKeys, key)
            }
            acl.permissionTarget = perm
            secserv.updateAcl(acl)
        } else if ((pathsize == 3 && oper in [';+', ';-'] ||
                    pathsize == 4 && oper == ':~') &&
                   path[0] in ['users', 'groups'] && path[2] == 'permissions') {
            // aces
            def isgroup = path[0] == 'groups'
            def principal = oper == ':~' ? path[3] : key
            def acl = infact.copyAcl(secserv.getAcl(principal))
            def aces = acl.aces
            if (oper == ';+') {
                def ace = infact.createAce()
                ace.principal = path[1]
                ace.group = isgroup
                ace.mask = makeMask(val)
                acl.aces = aces + ace
            } else {
                def ace = aces.find {
                    it.principal == path[1] && it.isGroup() == isgroup
                }
                if (oper == ';-') {
                    acl.aces = aces - ace
                } else {
                    def newace = infact.copyAce(ace)
                    newace.mask = makeMask(key)
                    acl.aces = aces - ace + newace
                }
            }
            secserv.updateAcl(acl)
        } else if (pathsize == 1 && oper in [';+', ';-'] &&
                   path[0] in ['users', 'groups', 'permissions']) {
            // users, groups, and permissions
            if (oper == ';+' && path[0] == 'users') {
                def user = infact.createUser()
                user.username = key
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
                secserv.createUser(user)
                for (prop in val.properties.entrySet()) {
                    modifyProp(secserv, 'create', key, prop.key, prop.value)
                }
                for (perm in val.permissions?.entrySet()) {
                    def ace = infact.createAce()
                    ace.principal = key
                    ace.group = false
                    ace.mask = makeMask(perm.value)
                    def acl = infact.copyAcl(secserv.getAcl(perm.key))
                    acl.aces = acl.aces + ace
                    secserv.updateAcl(acl)
                }
            } else if (oper == ';-' && path[0] == 'users') {
                secserv.deleteUser(key)
            } else if (oper == ';+' && path[0] == 'groups') {
                def group = infact.createGroup()
                group.groupName = key
                group.description = val.description
                group.newUserDefault = val.isdefault
                group.realm = val.realm
                group.realmAttributes = val.realmattrs
                secserv.createGroup(group)
                for (perm in val.permissions?.entrySet()) {
                    def ace = infact.createAce()
                    ace.principal = key
                    ace.group = true
                    ace.mask = makeMask(perm.value)
                    def acl = infact.copyAcl(secserv.getAcl(perm.key))
                    acl.aces = acl.aces + ace
                    secserv.updateAcl(acl)
                }
            } else if (oper == ';-' && path[0] == 'groups') {
                secserv.deleteGroup(key)
            } else if (oper == ';+' && path[0] == 'permissions') {
                def perm = infact.createPermissionTarget()
                perm.name = key
                perm.repoKeys = val.repos
                perm.includes = val.includes
                perm.excludes = val.excludes
                def acl = infact.createAcl()
                acl.permissionTarget = perm
                secserv.createAcl(acl)
            } else if (oper == ';-' && path[0] == 'permissions') {
                secserv.deleteAcl(secserv.getAcl(key).permissionTarget)
            }
        } else if ((pathsize == 3 && oper in [';+', ';-'] ||
                    pathsize == 4 && oper == ':~') &&
                   path[0] == 'users' && path[2] == 'properties') {
            // user properties, including API keys
            if (oper == ';+') {
                modifyProp(secserv, 'create', path[1], key, val)
            } else if (oper == ';-') {
                modifyProp(secserv, 'delete', path[1], key, null)
            } else if (oper == ':~') {
                modifyProp(secserv, 'update', path[1], path[3], key)
            }
        } else if (pathsize == 3 && oper in [':+', ':-'] &&
                   path[0] == 'users' && path[2] == 'groups') {
            // user/group memberships
            if (oper == ':+') {
                secserv.addUsersToGroup(key, [path[1]])
            } else {
                secserv.removeUsersFromGroup(key, [path[1]])
            }
        } else if (pathsize == 3 && oper == ':~' && path[0] == 'users') {
            // simple user attributes (email, is admin, etc)
            def user = infact.copyUser(secserv.findUser(path[1]))
            if (path[2] == 'password') {
                user.password = new SaltedPassword(key, user.salt)
            } else if (path[2] == 'salt') {
                user.password = new SaltedPassword(user.password, key)
            } else if (path[2] == 'email') {
                user.email = key
            } else if (path[2] == 'passkey') {
                user.genPasswordKey = key
            } else if (path[2] == 'admin') {
                user.admin = key
            } else if (path[2] == 'enabled') {
                user.enabled = key
            } else if (path[2] == 'updatable') {
                user.updatableProfile = key
            } else if (path[2] == 'privatekey') {
                user.privateKey = key
            } else if (path[2] == 'publickey') {
                user.publicKey = key
            } else if (path[2] == 'bintray') {
                user.bintrayAuth = key
            } else if (path[2] == 'locked') {
                user.locked = key
            } else if (path[2] == 'expired') {
                user.credentialsExpired = key
            }
            secserv.updateUser(user, true)
            if (path[2] == 'locked') {
                if (key) secserv.lockUser(path[1])
                else secserv.unlockUser(path[1])
            }
        } else if (pathsize == 3 && oper == ':~' && path[0] == 'groups') {
            // simple group attributes (description, is default, etc)
            def group = infact.copyGroup(secserv.findGroup(path[1]))
            if (path[2] == 'description') {
                group.description = key
            } else if (path[2] == 'isdefault') {
                group.newUserDefault = key
            } else if (path[2] == 'realm') {
                group.realm = key
            } else if (path[2] == 'realmattrs') {
                group.realmAttributes = key
            }
            secserv.updateGroup(group)
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
    encryptDecrypt(currver, true)
    encryptDecrypt(truever, true)
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
