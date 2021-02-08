/*
 * Copyright (C) 2021 JFrog Ltd.
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
import groovy.json.JsonOutput
import groovyx.net.http.HTTPBuilder

//import org.artifactory.addon.AddonsManager
//import org.artifactory.addon.plugin.PluginsAddon
//import org.artifactory.addon.plugin.build.AfterBuildSaveAction
//import org.artifactory.addon.plugin.build.BeforeBuildSaveAction
//import org.artifactory.api.jackson.JacksonReader
//import org.artifactory.api.rest.build.BuildInfo
//import org.artifactory.build.BuildInfoUtils
//import org.artifactory.build.BuildRun
//import org.artifactory.build.Builds
//import org.artifactory.build.DetailedBuildRun
//import org.artifactory.build.DetailedBuildRunImpl
//import org.artifactory.build.InternalBuildService
//import org.artifactory.concurrent.ArtifactoryRunnable
//import org.artifactory.exception.CancelException
//import org.artifactory.storage.build.service.BuildStoreService
//import org.artifactory.storage.db.DbService
//import org.artifactory.util.HttpUtils
//import org.artifactory.search.Searches
//import org.artifactory.request.RequestThreadLocal
//import sun.security.ssl.ContentType
//
//import java.util.List
//import java.util.concurrent.Callable
//import java.util.concurrent.Executors
//
//import org.apache.commons.lang.StringUtils
//import org.apache.http.HttpRequestInterceptor
//import org.apache.http.StatusLine
import org.apache.http.impl.client.AbstractHttpClient
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.impl.conn.PoolingClientConnectionManager
import org.apache.http.params.HttpParams

//import org.jfrog.build.api.Build
import org.slf4j.Logger
//import org.springframework.security.core.context.SecurityContextHolder

//import static groovyx.net.http.ContentType.BINARY
import static groovyx.net.http.ContentType.JSON
//import static groovyx.net.http.ContentType.TEXT
//import static groovyx.net.http.Method.DELETE
//import static groovyx.net.http.Method.PUT
import static groovyx.net.http.Method.POST


// Based on a specified set of repositories
// If user email address ends in a certain domain, the user shall be considered internal, and the extra checks skipped
//
// Endpoint one should send a json object that contains the IP address of the requesting client, and the repository in
// which access is being requested.  When it returns a json object that contains the field { “isAllowed” : “false” } it
// should return a 403 with a message “This download request has been blocked due to export control restrictions.  If
// you think this is in error please contact your account manager for resolution.”
//
// The second endpoint should send a json object that contains the user name, user email address, repo name, and repo
// path.  If it does not get a response including the field { “productEntitlementValid”: “true” } then it should return
// a 403: “You do not have permission to access this product, please contact your account manager for resolution”.

def settings = new Settings(ctx, log)
def compliance = new Compliance(log)

download {
    altResponse { request, responseRepoPath ->
        try {
            /*
            log.warn "Requesting artifact: $responseRepoPath"

            def artifactStatus = repositories.getProperties(responseRepoPath).getFirst('approver.status')
            if (artifactStatus && artifactStatus != 'approved') {
                status = 403
                message = 'This artifact wasn\'t approved yet, please use the Approver application.'
                log.warn "You asked for an unapproved artifact: $responseRepoPath. 403"
            }
            /*/
            def address = request.httpRequest.request.request.remoteAddr
            def repository = responseRepoPath.getRepoKey()
            def artifact = responseRepoPath.getPath()
            def user = security.currentUser()
            def username = security.getCurrentUsername()
            def email = user.getEmail()

            log.warn settings.getIpServer()
            log.warn username
            log.warn email
            log.warn repository
            log.warn artifact

            Status result = compliance.validate(settings, address, repository, artifact, username, email)

            status = result.status
            message = result.message
            //*/
        }
        catch(Exception e) {
            log.warn e.toString()
        }
    }
}

executions {
    test { params ->
        log.warn(log.getClass().toString())
        Status result = approve.validate(settings, "172.17.0.1", "test-repo", "dude", "dude@dude.net")

        log.warn(String.valueOf(result.status))
        log.warn(result.message)
    }
}

final class Globals {
    private Globals() {}

    // TODO Change to desired messsage responses
    final static SUCCESS = "success"
    final static FAILED = "failed"
    final static ERROR = 'error'
}

class Settings {
    Logger log
    String ipServer
    String entitlementServer
    String timeout // TODO
    String expires // TODO
    String flushCache // TODO

    Settings(ctx, log) {
        this.log = log
        File confFile = new File("${ctx.artifactoryHome.getEtcDir()}/plugins", "approveDeny.json")
        def reader

        try {
            reader = new FileReader(confFile)
            JsonSlurper slurper = new JsonSlurper()
            Map content = slurper.parse(reader)
            assert content instanceof Map

            this.ipServer = content.ipServer
            this.entitlementServer = content.entitlementServer
            this.timeout = content.timeout
            this.expires = content.expires
            this.flushCache = content.flushCache
        } finally {
            if (reader) {
                reader.close()
            }
        }
    }

    String getIpServer() {
        return this.ipServer
    }

    String getEntitlementServer() {
        return this.entitlementServer
    }

    String getTimeout() {
        return this.timeout
    }

    String getExpires() {
        return this.expires
    }

    String getFlushCache() {
        return this.flushCache
    }
}

class ThreadSafeHTTPBuilder extends HTTPBuilder {
    protected AbstractHttpClient createClient(HttpParams params) {
        PoolingClientConnectionManager cm = new PoolingClientConnectionManager()
        cm.setMaxTotal(200) // Increase max total connection to 200
        cm.setDefaultMaxPerRoute(20) // Increase default max connection per route to 20
        new DefaultHttpClient(cm, params)
    }
}

class Status {
    int status
    String message

    final static OK = 200
    final static FORBIDDEN = 403
    final static BAD_REQUEST = 400

    Status() {}

    Status(int status, String message) {
        this.status = status
        this.message = message
    }

    void setStatus(int status, String message) {
        this.status = status
        this.message = message
    }

    boolean success() {
        return status == Status.OK
    }
}

class Compliance {
    Logger log
    ThreadSafeHTTPBuilder http

    final static IS_ALLOWED = "isAllowed"
    final static PRODUCT_ENTITLEMENT_VALID = "productEntitlementValid"

    Compliance(log) {
        this.log = log
        this.http = new ThreadSafeHTTPBuilder()
    }

    Status validate(Settings settings, String address, String repository, String artifact, String username, String email) {
        def validateIpJson = {
            Address "${address}"
            Repository "${repository}"
        }

        Status result = validate_internal(settings.getIpServer(), validateIpServerJson, IS_ALLOWED, String.valueOf(true))

        if (result.status == Status.OK) {
            def validateEntitlementsJson = {
                Repository "${repository}"
                Artifact "${artifact}"
                Username "${username}"
                Email "${email}"
            }

            result = validate_internal(settings.getEntitlementServer(), validateEntitlementsJson, PRODUCT_ENTITLEMENT_VALID, String.valueOf(true))
        }

        return result;
    }

    private Status validate_internal(String uri, jsonObj, String key, String value) {
        Status result = new Status(Status.FORBIDDEN, Globals.FAILED)
        this.http.uri = uri

        this.http.request(POST) {
            requestContentType = JSON
            contentType = JSON
            body = JsonOutput.toJson(jsonObj).toString()
            this.log.warn JsonOutput.toJson(jsonObj).toString()

            response.success = { resp, json ->
                this.log.warn(Globals.SUCCESS)

                if (json.containsKey(key) && json.get(key) == value) {
                    result.setStatus(Status.OK, Globals.SUCCESS)
                }
            }
//            response.failure = { resp ->
//                this.log.warn(FAILED)
//                if (json && json.containsKey(IS_ALLOWED) && json.get(IS_ALLOWED) == String.valueOf(false)) {
//                    this.log.warn(FAILED)
//                    result.setStatus(403, Globals.FAILED)
//                }
//                else {
//                    this.log.warn "Unexpected error"
//                    result.setStatus(resp.status, Globals.ERROR)
//                }
//            }
        }

        return result
    }
}
