/*
 * Copyright (C) 2017 JFrog Ltd.
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

import groovy.json.JsonSlurper
import groovy.json.JsonException
import org.springframework.security.core.userdetails.UsernameNotFoundException

// This function needs to return a collection of usernames that should be
// cleaned (deleted) from Artifactory. Users in this array that already don't
// exist in Artifactory will be ignored. This function takes a map of properties
// extracted from cleanExternalUsers.json, and any configuration should go
// there. These properties can be overridden when calling the execution hook.
//
// By default, this function gets a list of deactivated users from Okta, but if
// this does not fit your needs, you need to implement your own function and use
// that instead.
def usersToClean(config) {
    def url = "https://$config.host/api/v1/apps/$config.appid/users"
    def conn = null, resp = null
    try {
        conn = new URL(url).openConnection()
        conn.setRequestProperty('Accept', 'application/json')
        conn.setRequestProperty('Authorization', "SSWS $config.apitoken")
        def stat = conn.responseCode
        if (stat < 200 || stat >= 300) {
            def msg = "Problem connecting to Okta: $stat $conn.responseMessage"
            throw new RuntimeException(msg)
        }
        resp = new JsonSlurper().parse(conn.inputStream)
    } finally {
        conn?.disconnect()
    }
    def names = resp.collect { it.credentials.userName.toLowerCase() }
    def allusrs = ctx.securityService.getAllUsers(false).collect { it.username }
    return allusrs - config.keepUsers - ['anonymous'] - names
}

executions {
    cleanExternalUsers() { params ->
        def msg = cleanUsers(params)
        status = 200
        message = msg
    }
}

cronEnabled = true

def getCronJob() {
    def defaultcron = "0 0 0 1 1 ?"
    def config = null
    def etcdir = ctx.artifactoryHome.haAwareEtcDir
    def cfgfile = new File(etcdir, "plugins/cleanExternalUsers.json")
    try {
        config = new JsonSlurper().parse(cfgfile)
    } catch (JsonException ex) {
        log.warn("Problem getting $cfgfile, disabling cron")
        cronEnabled = false
        return defaultcron
    }
    def cronjob = config?.cronjob
    if (cronjob) {
        log.warn("Config cron job is being set at: $cronjob")
        return cronjob
    }  else {
        log.warn("Cron job is not configured, disabling")
        cronEnabled = false
        return defaultcron
    }
}

jobs {
    cleanExternalUsersWorker(cron: getCronJob()) {
        if (cronEnabled) cleanUsers(null)
    }
}

def cleanUsers(params) {
    def etcdir = ctx.artifactoryHome.haAwareEtcDir
    def cfgfile = new File(etcdir, "plugins/cleanExternalUsers.json")
    def config = [dryRun: false]
    try {
        config = new JsonSlurper().parse(cfgfile)
    } catch (JsonException ex) {
        log.warn("Problem getting $cfgfile, ignoring")
    }
    if (params) {
        params.each { k, v ->
            if (v[0]) config[k] = v[0]
        }
    }
    def secserv = ctx.securityService
    def userct = 0
    for (user in usersToClean(config)) {
        def userfound = null
        try {
            userfound = secserv.findUser(user)
        } catch (UsernameNotFoundException ex) {
            userfound = null
        }
        if (userfound) {
            userct += 1
            if (config.dryRun) {
                log.warn("Deleting user $user (dry run)")
            } else {
                log.warn("Deleting user $user")
                secserv.deleteUser(user)
            }
        }
    }
    def retmsg = null
    if (config.dryRun) retmsg = "Deleted $userct users (dry run)"
    else retmsg = "Deleted $userct users"
    log.warn(retmsg)
    return retmsg
}
