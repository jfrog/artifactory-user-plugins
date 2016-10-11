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

//global variables 
httpclient = null

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
            log.debug("remote call url: ${url}")
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
        case 'data_retrieve':
            def url = "${baseurl}/api/plugins/execute/secRepData?params=action=${method}"
            req = new HttpPost(url)
            req.addHeader("User-Agent", HttpUtils.artifactoryUserAgent)
            req.addHeader("Content-Type", "application/json")
            req.addHeader("Authorization", auth)
            break
        default: 
            throw new RuntimeException("Invalid method ${method}.")
    }

    try {
        HttpResponse resp = getHttpClient().execute(req)
        //def statusCode = resp.getStatusLine().getStatusCode()
        //return statusCode
        return resp
    } catch (e) {
        HttpResponseFactory factory = new DefaultHttpResponseFactory()
        HttpResponse resp = factory.newHttpResponse(new BasicStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_NOT_ACCEPTABLE, null), null);
        return resp
    }
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
            def resp = remoteCall(it, auth, 'ping', null)
            if (resp.getStatusLine().getStatusCode() != 200) {
                throw new RuntimeException("Health Check Failed: ${it}: HTTP error code: " + resp.getStatusLine().getStatusCode())
            }

            slurped.securityReplication.whoami = "${it}"
            def builder = new JsonBuilder(slurped)

            //push new file to all instances 
            resp = remoteCall(it, auth, 'put', builder.toString())
            if (resp.getStatusLine().getStatusCode() != 200) {
                throw new RuntimeException("File Push Failed: ${it}: HTTP error code: " + resp.getStatusLine().getStatusCode())
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
    secRepData(httpMethod: 'POST') { params ->
        def action = params?.('action')?.get(0) as String
        switch (action) {
            case 'data_retrieve':
                def testFile = new File(artHome, "/plugins/testFile.json")
                try { 
                    slurped = new JsonSlurper().parseText(testFile.text)
                } catch (groovy.json.JsonException ex) {
                    log.error("Problem parsing JSON: ${ex}.message")
                }
                message = slurped
                status = 200
                break
            default: throw new RuntimeException("Invalide action ${action}")
        }
    }

    testSecurityDump(httpMethod: 'GET') { params ->
        status = 200
        message = new JsonBuilder(normalize(extract())).toPrettyString()
    }
}

jobs {
    securityReplicationWorker(cron: "*/30 * * * * ?") {
        MASTER = null
        DOWN_INSTANCE = []
        def artHome = ctx.artifactoryHome.haAwareEtcDir
        def targetFile = new File(artHome, "/plugins/securityReplication.json")
        def testFile = new File(artHome, "/plugins/testFile.json")
        try {
            slurped = new JsonSlurper().parseText(targetFile.text)
        } catch (groovy.json.JsonException ex) {
            log.error("Problem parsing JSON: ${ex}.message")
            return
        }

        try {
            slurped2 = new JsonSlurper().parseText(testFile.text)
        } catch (groovy.json.JsonException ex) {
            log.error("Problem parsing JSON: ${ex}.message")
        }

        def builder = new JsonBuilder(slurped2)
        def whoami = slurped.securityReplication.whoami
        def distList = slurped.securityReplication.urls
        def username = slurped.securityReplication.authorization.username
        def password = slurped.securityReplication.authorization.password
        def encoded = "$username:$password".getBytes().encodeBase64().toString()
        def auth = "Basic ${encoded}"    

        log.debug("whoami: ${whoami} \r\n")
        log.debug("Master: ${MASTER} \r\n")

        if (whoami == MASTER) {
            log.debug("I am the Master\r")
        } else {
            log.debug("I don't know who I am, checking if Master is up")

            //check if master is up
            for (i = 0; i < distList.size(); i++) {
                def instance = distList[i]
                
                //set master
                if (instance == whoami) {
                    log.debug("I am the Master setting Master\r")
                    MASTER = whoami
                    break
                }

                log.debug("checking if ${instance} is up")
                resp = remoteCall(instance, auth, 'ping', null)
                log.debug("ping statusCode: ${resp.getStatusLine().getStatusCode()}")
                if (resp.getStatusLine().getStatusCode() != 200) {
                    log.warn("MASTER: ${instance} instance is down, finding new master\r")
                    //ammend to instance down list
                    if (!DOWN_INSTANCE.contains(instance)){
                        DOWN_INSTANCE << instance
                    }
                } else {
                    log.debug("MASTER: ${instance} is up, setting MASTER \r")
                    MASTER = instance
                    if (DOWN_INSTANCE.contains(instance)) {
                        DOWN_INSTANCE.remove(instance)
                    }
                    break
                }
            }
        }

        log.debug("Down Instances are: ${DOWN_INSTANCE}")

        if (whoami != MASTER) {
            log.debug("I am a slave, going back to sleep")
            return
        } else {
            log.debug("I am the Master, starting to do work")
            
            log.debug("Checking my slave instances")
            for (i = 0; i < distList.size(); i++){
                def instance = distList[i]
                if ((instance == MASTER) && (whoami == MASTER)){
                    log.debug("This is myself (the Master), I'm up")
                } else {
                    resp = remoteCall(instance, auth, 'ping', null)
                    log.debug("ping statusCode: ${resp.getStatusLine().getStatusCode()}")
                    if (resp.getStatusLine().getStatusCode() != 200) {
                        log.warn("Slave: ${instance} instance is down, adding to DOWN_INSTANCE list")
                        if (!DOWN_INSTANCE.contains(instance)){
                            DOWN_INSTANCE << instance
                        }
                    } else {
                        log.debug("Slave: ${instance} instance is up")
                        if (DOWN_INSTANCE.contains(instance)){
                            DOWN_INSTANCE.remove(instance)
                        }
                    }
                }
            }
            log.debug("Down Instances again are: ${DOWN_INSTANCE}")
            log.debug("Going to slaves to get stuff")
            for (i = 0; i < distList.size(); i++){
                def instance = distList[i]
                if (instance == MASTER && MASTER == whoami) {
                    log.debug("This is myself (the Master), don't need to do any work here")
                } else if (DOWN_INSTANCE.contains(instance)) {
                    log.debug("${instance} is down, no need to do anything")
                } else {
                    log.debug("Accessing ${instance}, give me your stuff")
                    resp = remoteCall(instance, auth, 'data_retrieve', null)
                    if (resp.getStatusLine().getStatusCode() != 200) {
                        log.error("Error accessing ${instance}, failed to retrieve data")
                        break
                    }
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
                    log.info(sb.toString())
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
        // TODO read out the permissions mask
        def privs = it.getInt(3)
        def perm = permnames[it.getLong(4)]
        if (perm != null && userid != null) {
            if (!(userid in useraces)) useraces[userid] = [:]
            useraces[userid][perm] = privs
        }
        if (perm != null && groupid != null) {
            if (!(groupid in groupaces)) groupaces[groupid] = [:]
            groupaces[groupid][perm] = privs
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
