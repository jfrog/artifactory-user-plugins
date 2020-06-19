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

// v1.1.10

import groovy.json.JsonBuilder
import groovy.json.JsonException
import groovy.json.JsonSlurper

import java.security.MessageDigest

import org.apache.http.client.methods.*
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.HttpClients

import org.artifactory.api.config.VersionInfo
import org.artifactory.common.ArtifactoryHome
import org.artifactory.factory.InfoFactoryHolder
import org.artifactory.request.RequestThreadLocal
import org.artifactory.resource.ResourceStreamHandle
import org.artifactory.security.SaltedPassword
import org.artifactory.storage.db.servers.service.ArtifactoryServersCommonService
import org.artifactory.storage.fs.service.ConfigsService
import org.artifactory.util.HttpUtils

// This version number must be greater than or equal to the Artifactory version.
// Otherwise, security replication will not run. Always update this plugin when
// Artifactory is upgraded.
pluginVersion = "5.10.4"

/* to enable logging append this to the end of artifactorys logback.xml
    <logger name="securityReplication">
        <level value="debug"/>
    </logger>
*/

//global variables
verbose = false
artHome = ctx.artifactoryHome.etcDir
cronExpression = null
ignoredUsers = ['anonymous', '_internal', 'xray', 'access-admin']

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
    securityReplication(httpMethod: 'PUT') { params, ResourceStreamHandle body ->
        def set = (params?.getAt('set')?.getAt(0) ?: 0) as Integer
        if (set == 0 && ArtifactoryHome.get().isHaConfigured()) {
            def sserv = ctx.beanForType(ArtifactoryServersCommonService)
            def tctx = RequestThreadLocal.context.get().requestThreadLocal
            def auth = tctx.request.getHeader('Authorization')
            def data = wrapData('js', body.inputStream.text)
            for (node in sserv.activeMembers) {
                if (node.serverState.name() != 'RUNNING') continue
                def baseurl = node.contextUrl - ~'/$'
                def resp = remoteCall(null, baseurl, auth, 'json', data, 1)
                if (resp[1] > status) {
                    message = unwrapData('js', resp[0])
                    status = resp[1]
                }
            }
        } else {
            def targetFile = new File(artHome, '/plugins/securityReplication.json')
            try {
                targetFile.withOutputStream { it << body.inputStream }
                status = 200
            } catch (Exception ex) {
                message = "Problem writing file: $ex.message"
                status = 400
            }
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
    secRepDataGet(httpMethod: 'POST') { params, ResourceStreamHandle body ->
        def filter = (params?.getAt('filter')?.getAt(0) ?: null) as Integer
        def arg = wrapData('jo', null)
        log.debug("SLAVE: secRepDataGet is called")
        def bodytext = body.inputStream.text
        if (bodytext.length() > 0) {
            arg = wrapData('js', bodytext)
        }
        def (msg, stat) = getRecentPatch(arg, filter)
        message = unwrapData('js', msg)
        status = stat
    }

    //Usage: curl -X POST http://localhost:8081/artifactory/api/plugins/execute/secRepDataPost -d <data>
    //Internal Use
    secRepDataPost(httpMethod: 'POST') { params, ResourceStreamHandle body ->
        log.debug("SLAVE: secRepDataPost is called")
        def filter = (params?.getAt('filter')?.getAt(0) ?: null) as Integer
        def arg = wrapData('ji', body.inputStream)
        def (msg, stat) = applyAggregatePatch(arg, filter)
        message = unwrapData('js', msg)
        status = stat
    }

    //testing purposes only
    testSecurityDump(httpMethod: 'GET') { params ->
        status = 200
        message = new JsonBuilder(normalize(extract(null))).toPrettyString()
    }

    testDBUpdate() { params, ResourceStreamHandle body ->
        def json = new JsonSlurper().parse(body.inputStream)
        updateDatabase(null, json, null)
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

    secRepValidate(httpMethod: 'GET') { params ->
        def chain = params?.getAt('chain')?.getAt(0) ?: null
        log.debug("secRepValidate called with chain=$chain")
        if (chain == 'node') {
            message = new JsonBuilder(validateNode()).toString()
        } else if (chain == 'instance') {
            message = new JsonBuilder(validateInstance()).toString()
        } else if (chain == 'mesh') {
            message = new JsonBuilder(validateMesh()).toString()
        } else {
            message = validateResponse(validateMesh())
        }
        status = 200
    }

    //testing purposes only
    testRunSecurityReplication() {
        runSecurityReplication()
        status = 200
        message = "Security Replication executed"
    }
}

def validateResponse(data) {
    def errors = [], json = null, version = null
    if ('!message' in data) {
        def error = [:]
        error.title = 'Error accessing mesh'
        error.msg = data['!message']
        def rec = []
        rec << "Ensure securityReplication.json exists and is correct"
        error.rec = rec
        errors << error
    } else data.each { url, instance ->
        if (instance['!status'] != 200) {
            def error = ['instance': url]
            error.title = 'Error accessing instance'
            error.msg = instance['!message']
            error.status = instance['!status']
            def rec = []
            rec << "Ensure the instance is running and accessible"
            rec << "Ensure the plugin is installed and loaded correctly"
            rec << "Try reloading or reinstalling the plugin"
            rec << "Try restarting the Artifactory service on the instance"
            error.rec = rec
            errors << error
        } else instance.each { id, node ->
            if (id.startsWith('!')) return
            if (node['!status'] != 200 || node['!state'] != 'Running') {
                def error = ['instance': url, 'node': id]
                error.title = 'Error accessing node'
                error.msg = node['!message']
                error.status = node['!status'] + ' (' + node['!state'] + ')'
                def rec = []
                rec << "Ensure the node is running and accessible"
                rec << "Ensure the plugin is installed and loaded correctly"
                rec << "Try reloading or reinstalling the plugin"
                rec << "Try restarting the Artifactory service on the node"
                error.rec = rec
                errors << error
                return
            }
            if (!node.fsversion || node.fsversionerr) {
                def error = ['instance': url, 'node': id]
                error.title = 'Error reading plugin version from groovy file'
                error.msg = node.fsversionerr
                def rec = []
                rec << "Ensure the plugin is installed and loaded correctly"
                rec << "Ensure the correct version of the plugin is installed"
                rec << "Try reinstalling the plugin"
                error.rec = rec
                errors << error
            } else if (node.fsversion != node.version) {
                def error = ['instance': url, 'node': id]
                error.title = 'Loaded plugin version mismatch'
                error.msg = "Loaded plugin version '$node.version' does not"
                error.msg += " match groovy file verson '$node.fsversion'"
                def rec = []
                rec << "Run a plugin reload on the node"
                rec << "Ensure the plugin is installed and loaded correctly"
                rec << "Try restarting the Artifactory service on the node"
                error.rec = rec
                errors << error
            }
            if (!version) version = node.fsversion
            if (version != node.fsversion) {
                def error = ['instance': url, 'node': id]
                error.title = 'Plugin version mismatch'
                error.msg = "Plugin version '$node.fsversion' does not match"
                error.msg += " expected version '$version'"
                def rec = []
                rec << "Ensure the correct version of the plugin is installed"
                rec << "Try reinstalling the plugin"
                error.rec = rec
                errors << error
            }
            if (!node.json || node.jsonerr) {
                def error = ['instance': url, 'node': id]
                error.title = 'Error reading plugin config from json file'
                error.msg = node.jsonerr
                def rec = []
                rec << "Ensure the config file is installed and is correct"
                rec << "Try reinstalling the config file on the node"
                error.rec = rec
                errors << error
                return
            }
            def myjson = node.json
            def whoami = null
            if (myjson?.securityReplication?.whoami) {
                whoami = myjson.securityReplication.whoami
                myjson.securityReplication.whoami = null
            }
            if (!json) json = myjson
            if (myjson != json || whoami != url) {
                def error = ['instance': url, 'node': id]
                error.title = 'Plugin config mismatch'
                error.msg = "Plugin config does not match expected config:\n"
                node.json.securityReplication.whoami = whoami
                json.securityReplication.whoami = whoami
                error.msg += "Plugin config: "
                error.msg += new JsonBuilder(node.json).toPrettyString()
                error.msg += "\nExpected config: "
                error.msg += new JsonBuilder(json).toPrettyString()
                node.json.securityReplication.whoami = null
                json.securityReplication.whoami = null
                def rec = []
                rec << "Ensure the config file is installed and is correct"
                rec << "Try reinstalling the config file on the node"
                error.rec = rec
                errors << error
            }
            if (node.cron != myjson.securityReplication.cron_job) {
                def error = ['instance': url, 'node': id]
                error.title = 'Loaded plugin config mismatch'
                error.msg = "Loaded plugin cron expression '$node.cron' does"
                error.msg += " not match configured cron expression"
                error.msg += " '$myjson.securityReplication.cron_job'"
                def rec = []
                rec << "Run a plugin reload on the node"
                rec << "Ensure the plugin is installed and loaded correctly"
                rec << "Try restarting the Artifactory service on the node"
                error.rec = rec
                errors << error
            }
        }
    }
    def status = new StringBuilder('==== ')
    if (errors.size() > 0) {
        status << 'Failure: ' << errors.size() << ' errors ====\n'
    } else {
        status << 'Success! All nodes synced with securityReplication version '
        status << version << ' ====\n'
    }
    for (err in errors) {
        status << '#### ' << err.title << ':'
        if (err.instance) {
            status << ' ' << err.instance
            if (err.node) status << ' - ' << err.node
            if (err.status) status << ' | ' << err.status
        }
        def msg = err.msg.replaceAll(/(?m)^/, '        ')
        status << '\n' << msg << '\n    Recommended Actions:\n'
        for (rec in err.rec) status << '     - ' << rec << '\n'
    }
    return status.toString()
}

def validateMesh() {
    log.debug("validating mesh ...")
    def slurped = null
    def targetFile = new File(artHome, "/plugins/securityReplication.json")
    try {
        slurped = new JsonSlurper().parse(targetFile)
    } catch (JsonException ex) {
        def msg = "Cannot read urls from securityReplication.json: $ex"
        return ['!message': msg]
    }
    def username = slurped.securityReplication.authorization.username
    def password = slurped.securityReplication.authorization.password
    def encoded = "$username:$password".getBytes().encodeBase64().toString()
    def auth = "Basic $encoded"
    def instances = [:]
    def urls = slurped.securityReplication.urls.reverse().unique()
    while (urls) {
        def url = urls.pop()
        instances[url] = validateRequest(url, auth, 'instance')
        for (serv in (instances[url].entrySet() as List).reverse()) {
            if (serv.key.startsWith('!')) continue
            for (link in serv.value?.json?.securityReplication?.urls?.reverse()) {
                if (!(link in urls) && !(link in instances) && (link != url)) {
                    urls << link
                }
            }
        }
    }
    log.debug("mesh validation data fetched: $instances")
    return instances
}

def validateInstance() {
    log.debug("validating instance ...")
    def servs = [:]
    def tctx = RequestThreadLocal.context.get().requestThreadLocal
    def auth = tctx.request.getHeader('Authorization')
    def sserv = ctx.beanForType(ArtifactoryServersCommonService)
    def me = sserv.currentMember
    for (serv in sserv.allArtifactoryServers) {
        def node = null
        if (serv == me) {
            node = validateNode()
            node['!status'] = 200
        } else node = validateRequest(serv.contextUrl, auth, 'node')
        node['!state'] = serv.serverState.prettyName
        servs[serv.serverId] = node
    }
    log.debug("instance validation data fetched: $servs")
    return servs
}

def validateNode() {
    log.debug("validating node ...")
    def data = ['version': pluginVersion, 'cron': cronExpression]
    def configfile = new File(artHome, "/plugins/securityReplication.json")
    try {
        data['json'] = new JsonSlurper().parse(configfile)
    } catch (JsonException ex) {
        data['jsonerr'] = "Cannot read securityReplication.json: $ex"
    }
    def pluginfile = new File(artHome, "/plugins/securityReplication.groovy")
    try {
        def version = null
        try {
            pluginfile.eachLine {
                if (!it.startsWith("pluginVersion = \"")) return
                def match = it =~ '^pluginVersion = "(.*)"$'
                if (match.size() <= 0) return
                version = match[0][1]
                throw new RuntimeException('<terminating early>')
            }
        } catch (RuntimeException ex) {
            if (ex.message != '<terminating early>') throw ex
        }
        if (version) data['fsversion'] = version
        else data['fsversionerr'] = "Cannot find version string in plugin file."
    } catch (Exception ex) {
        data['fsversionerr'] = "Cannot read securityReplication.groovy: $ex"
    }
    log.debug("node validation data fetched: $data")
    return data
}

def validateRequest(baseurl, auth, chain) {
    def resp = null, result = [:]
    def url = (baseurl - ~'/$') + '/api/plugins/execute/secRepValidate'
    url += '?params=chain=' + chain
    try {
        resp = makeRequest(new HttpGet(url), auth)
    } catch (Exception ex) {
        def msg = "Problem making request: $ex"
        resp = [wrapData('js', msg), -1]
    }
    try {
        if (resp[1] == 200) result = unwrapData('jo', resp[0])
        else result['!message'] = unwrapData('js', resp[0])
    } catch (Exception ex) {
        def msg = "Problem parsing response data: $ex"
        resp = [wrapData('js', msg), -1]
    }
    result['!status'] = resp[1]
    return result
}

def getCronJob() {
    def defaultcron = "0 0 0/1 * * ?"
    def slurped = null
    def jsonFile = new File(artHome, "/plugins/securityReplication.json")
    try {
        slurped = new JsonSlurper().parse(jsonFile)
    } catch (JsonException ex) {
        log.error("ALL: problem getting $jsonFile, using default")
        cronExpression = defaultcron
        return defaultcron
    }
    def cron_job = slurped?.securityReplication?.cron_job
    if (cron_job) {
        log.debug("ALL: config cron job is being set at: $cron_job")
        cronExpression = cron_job
        return cron_job
    }  else {
        log.debug("ALL: cron job is not configured, using default")
        cronExpression = defaultcron
        return defaultcron
    }
}

//general artifactory cron job hook
jobs {
    securityReplicationWorker(cron: getCronJob()) {
        runSecurityReplication()
    }
}

def runSecurityReplication() {
    def slurped = null
    def targetFile = new File(artHome, "/plugins/securityReplication.json")
    try {
        slurped = new JsonSlurper().parse(targetFile)
    } catch (JsonException ex) {
        log.error("ALL: problem getting $targetFile")
        return
    }
    def filter = slurped.securityReplication.filter
    def whoami = slurped.securityReplication.whoami
    def distList = slurped.securityReplication.urls
    if (distList.size() <= 1) {
        log.debug("ALL: I'm all alone here, no need to do work")
        return
    }
    def username = slurped.securityReplication.authorization.username
    def password = slurped.securityReplication.authorization.password
    def encoded = "$username:$password".getBytes().encodeBase64().toString()
    def auth = "Basic $encoded"
    def upList = checkInstances(distList, whoami, auth)
    if (upList == null) return
    log.debug("MASTER: upList size: ${upList.size()}, distList size: ${distList.size()}")
    if (2*upList.size() <= distList.size()) {
        log.debug("MASTER: Cannot continue, majority of instances unavailable")
        return
    }
    log.debug("MASTER: Available instances are: $upList")
    def ver = getArtifactoryVersion()
    if (incompatibleVersions(ver, upList.values(), 5, 6)) {
        log.debug("MASTER: Cannot continue, some instances are incompatible versions")
        return
    }
    if (slurped.securityReplication.safety != 'off' && checkArtifactoryVersion(ver[0])) {
        log.error("MASTER: Cannot continue, Artifactory version is too new; please update this plugin")
        return
    }
    upList = simplifyFingerprints(upList)
    log.debug("MASTER: Let's do some updates")
    log.debug("MASTER: Getting the golden file")
    def golden = findBestGolden(upList, whoami, auth)
    log.debug("MASTER: Going to slaves to get stuff")
    def bigDiff = grabStuffFromSlaves(golden, upList, whoami, auth, filter)
    if (verbose == true) {
        log.debug("MASTER: The aggragated diff is: $bigDiff")
    }
    def mergedPatch = merge(golden.golden, bigDiff)
    if (verbose == true) {
        log.debug("MASTER: the merged golden patch is $mergedPatch")
    }
    log.debug("MASTER: I gotta send the golden copy back to my slaves")
    sendSlavesGoldenCopy(upList, whoami, auth, mergedPatch, golden, filter)
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

def incompatibleVersions(ver, upList, maj, min) {
    def isnew = compareVersions(ver[0], maj, min) >= 0
    for (v in ver) {
        if ((compareVersions(v, maj, min) >= 0) != isnew) return true
    }
    for (inst in upList) {
        if (!inst?.version) return true
        for (vers in inst.version) {
            if ((compareVersions(vers, maj, min) >= 0) != isnew) return true
        }
    }
    return false
}

def compareVersions(version, major, minor) {
    def vers = version.split('\\.')
    def maj = vers[0] as int
    def min = vers[1] as int
    return (maj != major) ? (maj <=> major) : (min <=> minor)
}

def checkArtifactoryVersion(version) {
    def artvers = version.split('[-.]')
    def plugvers = pluginVersion.split('\\.')
    def artmaj = artvers[0] as int
    def plugmaj = plugvers[0] as int
    if (artmaj != plugmaj) return artmaj > plugmaj
    def artmid = artvers[1] as int
    def plugmid = plugvers[1] as int
    if (artmid != plugmid) return artmid > plugmid
    def artmin = artvers[2] as int
    def plugmin = plugvers[2] as int
    return artmin > plugmin
}

def simplifyFingerprints(upList) {
    return upList.collectEntries { k, v ->
        [k, (v?.cs && v?.ts) ? ['cs': v.cs, 'ts': v.ts] : null]
    }
}

def getArtifactoryVersion() {
    if (!ArtifactoryHome.get().isHaConfigured()) {
        return [ctx.centralConfig.versionInfo.version]
    }
    def sserv = ctx.beanForType(ArtifactoryServersCommonService)
    return sserv.allArtifactoryServers*.artifactoryVersion
}

def remoteCall(whoami, baseurl, auth, method, data = wrapData('jo', null), filter = null) {
    def exurl = "$baseurl/api/plugins/execute"
    def me = whoami == baseurl
    try {
        switch(method) {
            case 'json':
                def setstr = (filter == null) ? '' : "?params=set=$filter"
                def req = new HttpPut("$exurl/securityReplication$setstr")
                return makeRequest(req, auth, data, "text/plain")
            case 'plugin':
                def req = new HttpPut("$baseurl/api/plugins/securityReplication")
                return makeRequest(req, auth, data, "text/plain")
            case 'data_send':
                def datacp = wrapData('js', unwrapData('js', data))
                if (me) return applyAggregatePatch(datacp, filter)
                def filterstr = (filter == null) ? '' : "?params=filter=$filter"
                def req = new HttpPost("$exurl/secRepDataPost$filterstr")
                return makeRequest(req, auth, data, "application/json")
            case 'data_retrieve':
                if (me) return getRecentPatch(data, filter)
                def filterstr = (filter == null) ? '' : "?params=filter=$filter"
                def req = new HttpPost("$exurl/secRepDataGet$filterstr")
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
    } catch (Exception ex) {
        def writer = new StringWriter()
        writer.withPrintWriter { ex.printStackTrace(it) }
        log.error(writer.toString())
        return [wrapData('js', "Exception during call: $ex.message"), 500]
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
        log.error("Problem making request: $ex.message")
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
        if (whoami != dist) {
            //push plugin to all instances
            log.debug("sending plugin to $dist instance")
            resp = remoteCall(whoami, dist, auth, 'plugin', pluginData)
            if (resp[1] != 200) {
                return [wrapData('js', "PLUGIN Push $dist Failed: ${resp[0][2]}"), resp[1]]
            }
        }
        //push json to all instances
        log.debug("sending json file to $dist instance")
        slurped.securityReplication.whoami = "$dist"
        resp = remoteCall(whoami, dist, auth, 'json', wrapData('jo', slurped))
        if (resp[1] != 200) {
            return [wrapData('js', "JSON Push $dist Failed: ${resp[0][2]}"), resp[1]]
        }
    }
    return [wrapData('js', "Pushed all data successfully"), 200]
}

def findBestGolden(upList, whoami, auth) {
    def baseSnapShot = [:]
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

def sendSlavesGoldenCopy(upList, whoami, auth, mergedPatch, golden, filter) {
    def fingerprint = [cs: null, ts: System.currentTimeMillis()]
    fingerprint.version = getArtifactoryVersion()
    if (golden.fingerprint && fingerprint.ts <= golden.fingerprint.ts) {
        fingerprint.ts = 1 + golden.fingerprint.ts
    }
    for (instance in upList.entrySet()) {
        log.debug("MASTER: Sending mergedPatch to $instance.key")
        def data = wrapData('jo', [fingerprint: fingerprint, patch: mergedPatch])
        def resp = remoteCall(whoami, instance.key, auth, 'data_send', data, filter)
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

def checkInstances(distList, whoami, auth) {
    log.debug("ALL: I don't know who I am, checking if Master is up")
    def upList = [:], master = false
    for (instance in distList) {
        log.debug("ALL: Checking if $instance is up")
        def resp = remoteCall(whoami, instance, auth, 'ping')
        log.debug("ALL: ping statusCode: ${resp[1]}")
        if (resp[1] != 200) {
            log.warn("ALL: $instance instance is down. Status code: ${resp[1]}")
        } else {
            log.debug("ALL: $instance is up")
            if (!master) {
                log.debug("ALL: whoami: $whoami")
                log.debug("ALL: Master: $instance")
                if (whoami != instance) {
                    log.debug("SLAVE: I am a slave, going back to sleep")
                    return null
                }
                log.debug("MASTER: I am the Master, starting to do work")
                log.debug("MASTER: Checking my slave instances")
                master = true
            }
            try {
                def data = unwrapData('jo', resp[0])
                if (data == null) upList[instance] = ['version': null]
                else upList[instance] = data
            } catch (Exception ex) {
                upList[instance] = ['version': null]
            }
        }
    }
    if (!master) {
        def msg = "Cannot find master. Please check the configuration file and"
        msg += " ensure that $whoami is in the urls list."
        throw new RuntimeException(msg)
    }
    return upList
}

def grabStuffFromSlaves(mygolden, upList, whoami, auth, filter) {
    def bigDiff = []
    for (inst in upList.entrySet()) {
        log.debug("MASTER: Accessing $inst.key, give me your stuff")
        def resp = null
        def golden = mygolden.collectEntries { k, v -> [k, v] }
        def data = wrapData('jo', golden)
        if (golden.fingerprint && golden.fingerprint.cs == inst.value?.cs) {
            resp = remoteCall(whoami, inst.key, auth, 'data_retrieve', wrapData('jo', null), filter)
        } else {
            if (golden.fingerprint == null) golden.fingerprint = [:]
            golden.fingerprint.version = getArtifactoryVersion()
            resp = remoteCall(whoami, inst.key, auth, 'data_retrieve', data, filter)
        }
        if (resp[1] != 200) {
            throw new RuntimeException("failed to retrieve data from ${inst.key}. Status code: ${resp[1]}")
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
        def fingerprint = is ? unwrapData('jo', is) : [:]
        fingerprint.version = getArtifactoryVersion()
        return [wrapData('jo', fingerprint), 200]
    } finally {
        if (is) unwrapData('ji', is).close()
    }
}

def getRecentPatch(newgolden, filter) {
    def baseSnapShot = [:]
    def newgoldenuw = unwrapData('jo', newgolden)
    def goldenDB = newgoldenuw?.golden
    def extracted = extract(filter)
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
        def ver = getArtifactoryVersion()
        if (incompatibleVersions(ver, [newgoldenuw.fingerprint], 5, 6)) {
            def msg = "Cannot merge golden, incompatible version."
            log.error(msg)
            return [wrapData('js', msg), 400]
        }
        def goldendiff = buildDiff(baseSnapShot, goldenDB)
        def extractdiff = buildDiff(baseSnapShot, extracted)
        def mergeddiff = merge(baseSnapShot, [goldendiff, extractdiff])
        extracted = applyDiff(baseSnapShot, mergeddiff)
        updateDatabase(null, extracted, filter)
        newgoldenuw.fingerprint.version = ver
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

def applyAggregatePatch(newpatch, filter) {
    def goldenDB = null, oldGoldenDB = null
    def patch = unwrapData('jo', newpatch)
    def ver = getArtifactoryVersion()
    if (incompatibleVersions(ver, [patch.fingerprint], 5, 6)) {
        def msg = "Cannot update, incompatible version."
        log.error(msg)
        return [wrapData('js', msg), 400]
    }
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
    def masterver = patch.fingerprint.version
    patch.fingerprint.version = ver
    writeFile('fingerprint', wrapData('jo', patch.fingerprint))
    writeFile('golden', wrapData('jo', goldenDB))
    is = null
    try {
        is = readFile('recent')
        if (!is) throw new JsonException("Recent file not found.")
        def extracted = unwrapData('jo', is)
        updateDatabase(extracted, goldenDB, filter)
    } catch (JsonException ex) {
        log.error("Could not parse recent.json: $ex.message")
        return [wrapData('js', "Could not parse recent.json: $ex.message"), 400]
    } finally {
        if (is) unwrapData('ji', is).close()
    }
    patch.fingerprint.version = masterver
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
//       "admin": <boolean>,
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
    def encodedKeyPair = null, encodedKeyPairFromDecoded = null
    def decodedKeyPair = null
    def decryptionStatusHolder = null
    try {
        def statpath = "org.jfrog.security.crypto.result.DecryptionStatusHolder"
        def encodedkeypairpath = "org.jfrog.security.crypto.EncodedKeyPair"
        def decodedkeypairpath = "org.jfrog.security.crypto.DecodedKeyPair"
        decryptionStatusHolder = Class.forName(statpath)
        def encodedkeypairclass = Class.forName(encodedkeypairpath)
        def decodedkeypairclass = Class.forName(decodedkeypairpath)
        encodedKeyPair = encodedkeypairclass.getConstructor(String, String)
        for (constructor in encodedkeypairclass.getConstructors()) {
            if (constructor.parameterCount != 2) continue;
            if (constructor.parameterTypes[0] == String) continue;
            encodedKeyPairFromDecoded = constructor
        }
        for (constructor in decodedkeypairclass.getConstructors()) {
            if (constructor.parameterCount != 1) continue;
            decodedKeyPair = constructor
        }
        if (encodedKeyPairFromDecoded == null || decodedKeyPair == null) {
            def msg = "Could not find classes required for encryption, assuming"
            msg += " an old version of Artifactory."
            log.debug(msg)
            return
        }
    } catch (ClassNotFoundException ex) {
        def msg = "Could not find classes required for encryption, assuming"
        msg += " an old version of Artifactory."
        log.debug(msg)
        return
    } catch (NoSuchMethodException ex) {
        def msg = "Could not find classes required for encryption, assuming"
        msg += " an old version of Artifactory."
        log.debug(msg)
        return
    }
    def home = ArtifactoryHome.get()
    def is5x = false, cryptoHelper = null, masterWrapper = null
    try {
        def art4ch = "org.artifactory.security.crypto.CryptoHelper"
        cryptoHelper = Class.forName(art4ch)
        if (!cryptoHelper.hasMasterKey()) return
    } catch (ClassNotFoundException ex) {
        is5x = true
        def art5ch = "org.artifactory.common.crypto.CryptoHelper"
        cryptoHelper = Class.forName(art5ch)
        try {
            if (!cryptoHelper.hasArtifactoryKey(home)) return
        } catch (MissingMethodException ex2) {
            if (!cryptoHelper.hasMasterKey(home)) return
        }
    }
    def encprops = ['basictoken', 'ssh.basictoken', 'sumologic.access.token',
                    'sumologic.refresh.token', 'docker.basictoken', 'apiKey']

    try {
        masterWrapper = ArtifactoryHome.get().getArtifactoryEncryptionWrapper()
    } catch (MissingMethodException ex) {
        masterWrapper = ArtifactoryHome.get().getMasterEncryptionWrapper()
    }
    def encryptWrapper = encrypt ? masterWrapper : null
    def decryptWrapper = encrypt ? null : masterWrapper
    for (user in json.users.values()) {
        if (user.privatekey && user.publickey) {
            def pair = encodedKeyPair.newInstance(user.privatekey, user.publickey)
            try {
                def status = decryptionStatusHolder.newInstance()
                pair = decodedKeyPair.newInstance(pair.decode(decryptWrapper, status).createKeyPair())
            } catch (MissingMethodException ex) {
                pair = pair.decode(decryptWrapper)
            }
            pair = encodedKeyPairFromDecoded.newInstance(pair, encryptWrapper)
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

def extract(filter) {
    def secserv = ctx.securityService
    def result = [:]
    // get the filter settings from the config file
    if (filter == null) {
        def secRepJson = new File(artHome, '/plugins/securityReplication.json')
        try {
            def slurped = new JsonSlurper().parse(secRepJson)
            filter = slurped?.securityReplication?.filter ?: 1
        } catch (JsonException ex) {
            filter = 1
        }
    }
    def msg = "ALL: Using filter level $filter, replicating"
    if (filter < 1) msg += " nothing"
    else {
        if (filter >= 1) msg += " users"
        if (filter >= 2) msg += ", groups"
        if (filter >= 3) msg += ", permissions"
    }
    log.debug(msg)
    // permissions
    if (filter >= 3) {
        def perms = [:], acls = secserv.allAcls
        for (acl in acls) {
            def perm = [:]
            perm.includes = acl.permissionTarget.includes
            perm.excludes = acl.permissionTarget.excludes
            perm.repos = acl.permissionTarget.repoKeys.collect { it - ~'-cache$' }
            perms[acl.permissionTarget.name] = perm
        }
        result['permissions'] = perms
    }
    // groups
    if (filter >= 2) {
        def groups = [:], grps = null
        try {
            grps = secserv.allGroups
        } catch (MissingPropertyException ex) {
            grps = secserv.getAllGroups(true)
        }
        for (grp in grps) {
            def group = [:]
            group.description = grp.description
            group.isdefault = grp.isNewUserDefault()
            group.realm = grp.realm
            group.realmattrs = grp.realmAttributes
            try {
                group.admin = grp.isAdminPrivileges()
            } catch (MissingMethodException ex) {
                group.admin = false
            }
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
        def users = [:], usrs = null
        try {
            usrs = secserv.getAllUsers(true, true)
        } catch (MissingMethodException ex) {
            usrs = secserv.getAllUsers(true)
        }
        for (usr in usrs) {
            if (usr.username in ignoredUsers) continue
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
            try {
                secserv.deleteUserProperty(user, key)
            } catch (MissingMethodException ex) {
                secserv.deleteProperty(user, key)
            }
        } else if (op == 'update') {
            try {
                if (secserv.deleteUserProperty(user, key)) {
                    secserv.addUserProperty(user, key, val)
                }
            } catch (MissingMethodException ex) {
                if (secserv.deleteProperty(user, key)) {
                    secserv.addUserProperty(user, key, val)
                }
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
            if (!isgroup && path[1] in ignoredUsers) continue
            def principal = oper == ':~' ? path[3] : key
            def acl = secserv.getAcl(principal)
            if (acl == null) continue
            acl = infact.copyAcl(acl)
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
                if (key in ignoredUsers) continue
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
                    def acl = secserv.getAcl(perm.key)
                    if (acl == null) continue
                    acl = infact.copyAcl(acl)
                    acl.aces = acl.aces + ace
                    secserv.updateAcl(acl)
                }
            } else if (oper == ';-' && path[0] == 'users') {
                if (key in ignoredUsers) continue
                secserv.deleteUser(key)
            } else if (oper == ';+' && path[0] == 'groups') {
                def group = infact.createGroup()
                group.groupName = key
                group.description = val.description
                group.newUserDefault = val.isdefault
                group.realm = val.realm
                group.realmAttributes = val.realmattrs
                try {
                    group.adminPrivileges = val.admin ?: false
                } catch (MissingPropertyException ex) {}
                secserv.createGroup(group)
                for (perm in val.permissions?.entrySet()) {
                    def ace = infact.createAce()
                    ace.principal = key
                    ace.group = true
                    ace.mask = makeMask(perm.value)
                    def acl = secserv.getAcl(perm.key)
                    if (acl == null) continue
                    acl = infact.copyAcl(acl)
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
            if (path[1] in ignoredUsers) continue
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
            if (path[1] in ignoredUsers) continue
            // user/group memberships
            try {
                if (oper == ':+') {
                    secserv.addUsersToGroup(key, [path[1]])
                } else {
                    secserv.removeUsersFromGroup(key, [path[1]])
                }
            } catch (RuntimeException ex) {
                log.debug("Exception changing group membership: $ex")
            }
        } else if (pathsize == 3 && oper == ':~' && path[0] == 'users') {
            if (path[1] in ignoredUsers) continue
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
            } else if (path[2] == 'admin') {
                try {
                    group.adminPrivileges = key
                } catch (MissingPropertyException ex) {}
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

def expandPatch(base, line) {
    if (line[1] != ';+') return [line]
    if (!(line[2] in ['users', 'groups', 'permissions'])) return [line]
    def result = [], lnsize = line[0].size()
    if (lnsize == 0) {
        if (!(line[2] in base)) base[line[2]] = [:]
        return line[3].collect { k, v ->
            [[line[2]], ';+', k, v]
        }
    } else if (lnsize == 2 && line[2] == 'permissions') {
        if (line[0][0] == 'permissions') return [line]
        if (!(line[2] in base[line[0][0]][line[0][1]])) {
            base[line[0][0]][line[0][1]][line[2]] = [:]
        }
        return line[3].collect { k, v ->
            [[line[0][0], line[0][1], line[2]], ';+', k, v]
        }
    } else if (lnsize == 2 && line[2] == 'groups') {
        if (line[0][0] != 'users') return [line]
        if (!(line[2] in base[line[0][0]][line[0][1]])) {
            base[line[0][0]][line[0][1]][line[2]] = []
        }
        return line[3].collect {
            [[line[0][0], line[0][1], line[2]], ':+', it]
        }
    } else return [line]
}

def merge(base, patches) {
    def result = []
    def norm = normalize(base)
    // `patches` is a list of patchsets (a patchset is a list of changes).
    // `result` should be a single patchset, which is all the patchsets in
    // `patches` concatenated.
    patches.each { xs -> xs.each { x -> result.addAll(expandPatch(norm, x)) } }
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
    return buildDiff(base, applyDiff(norm, result))
}

def buildDiff(oldver, newver) {
    def ptch = []
    diff([], ptch, normalize(oldver), normalize(newver))
    return ptch
}

def applyDiff(oldver, ptch) {
    return patch([], ptch.sort(diffsort), normalize(oldver))
}

def updatePasswords(snapshot) {
    for (user in snapshot.users.values()) {
        def pass = user.password, salt = user.salt
        if (salt != null && pass ==~ '[a-f0-9]{32}') {
            user.password = 'md5$1$' + salt + '$' + pass
            user.salt = null
        }
    }
}

def updateDatabase(oldver, newver, filter) {
    // current state of the database (might have changed since oldver)
    def currver = extract(filter)
    if (oldver == null) oldver = currver
    // update any old passwords if necessary
    if (compareVersions(ctx.centralConfig.versionInfo.version, 5, 6) >= 0) {
        updatePasswords(oldver)
        updatePasswords(newver)
    }
    // newver from oldver
    def newdiff = buildDiff(oldver, newver)
    // currver from oldver
    def currdiff = buildDiff(oldver, currver)
    // newver + currver from oldver
    def truediff = merge(oldver, [currdiff, newdiff])
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
