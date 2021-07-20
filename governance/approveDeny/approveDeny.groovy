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

import org.apache.http.impl.client.AbstractHttpClient
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.impl.conn.PoolingClientConnectionManager
import org.apache.http.params.HttpParams

import org.slf4j.Logger

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

import static groovyx.net.http.ContentType.JSON
import static groovyx.net.http.Method.POST


def settings = new Settings(ctx, log)
def compliance = new Compliance(log)

def UNINITIALIZED = "null"

download {
    altResponse { request, responseRepoPath ->
        try {
            log.info "Requesting artifact: $responseRepoPath"

            def transaction_ip = request.clientAddress
            def repository_name = responseRepoPath.getRepoKey()
            def app_name = UNINITIALIZED

            def user = security.currentUser()
            def email = user.getEmail()

            String[] items = responseRepoPath.getPath().split("/");
            def product_name_space = UNINITIALIZED
            int index = 0

            if (items.length == 4) {
                product_name_space = items[0]
                index++
            }

            def module_name = items[index]
            def version = items[index + 1]

            if (items[index + 2].compareTo("manifest.json") == 0) {
                log.info "ipServer: ${settings.getIpServer()}"
                log.info "entitlementServer: ${settings.getEntitlementServer()}"
                log.info "transaction_ip: ${transaction_ip}"
                log.info "app_name: ${app_name}"
                log.info "repository_name: ${repository_name}"
                log.info "module_name: ${module_name}"
                log.info "product_name_space: ${product_name_space}"
                log.info "version: ${version}"
                log.info "email: ${email}"

                Result result = compliance.validate(settings, transaction_ip, app_name, repository_name, module_name, product_name_space, version, email)

                status = result.status // NOTE: status can only be assigned once
            }
        }
        catch(Exception e) {
            log.warn e.toString()
        }
    }
}

executions {
    test { params ->
        log.warn(log.getClass().toString())
        Result result = approve.validate(settings, "172.17.0.1", "test-repo", "admin", "null")

        log.warn(String.valueOf(result.status))
        log.warn(result.message)
    }

    getcachesize { params ->
        try {
            def jsonObj = {
                CacheSize "${compliance.getCacheSize()}"
            }
            message = JsonOutput.toJson(jsonObj).toString()
            status = 200
        }
        catch(Exception e) {
            status = 400
        }
    }

    purgecache { params ->
        try {
            compliance.purgeCache()
            status = 200
        }
        catch(Exception e) {
            status = 400
        }
    }
}

final class Globals {
    private Globals() {}

    final static SUCCESS = "success"
    final static FAILED = "failed"
    final static ERROR = 'error'

    final static int TIMEOUT = 10 // This is in minutes
}

class Settings {
    Logger log
    String ipServer
    String entitlementServer

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
}

class ThreadSafeHTTPBuilder extends HTTPBuilder {
    protected AbstractHttpClient createClient(HttpParams params) {
        PoolingClientConnectionManager cm = new PoolingClientConnectionManager()
        cm.setMaxTotal(200) // Increase max total connection to 200
        cm.setDefaultMaxPerRoute(20) // Increase default max connection per route to 20
        new DefaultHttpClient(cm, params)
    }
}

class Result {
    int status
    String message

    final static OK = 200
    final static FORBIDDEN = 403

    Result(int status, String message) {
        this.status = status
        this.message = message
    }

    void setStatus(int status, String message) {
        this.status = status
        this.message = message
    }

    boolean success() {
        return status == Result.OK
    }
}

class CacheItem {
    String jsonStr
    Result result
    LocalDate date = LocalDate.now()
    LocalTime time = LocalTime.now()

    CacheItem(def jsonObj, Result result) {
        this.jsonStr = JsonOutput.toJson(jsonObj).toString()
        this.result = result
    }

    boolean isValid(def jsonObj) {
        LocalDate currentDate = LocalDate.now()
        LocalTime currentTime = LocalTime.now()
        LocalTime endTime = this.time.plus(Duration.ofMinutes(Globals.TIMEOUT))

        if (currentDate.isEqual(this.date)) {
            if (currentTime.isAfter(this.time)) {
                if (currentTime.isBefore(endTime)) {
                    def jsonTemp = JsonOutput.toJson(jsonObj).toString()
                    if (this.jsonStr.contentEquals(jsonTemp)) {
                        return true
                    }
                }
            }
        }

        return false
    }

    Result getResult() {
        return this.result
    }
}

class Compliance {
    Logger log
    ThreadSafeHTTPBuilder http
    HashMap cache = new HashMap()

    final static STATUS = "status"
    final static APPROVED = "Approved"

    Compliance(log) {
        this.log = log
        this.http = new ThreadSafeHTTPBuilder()
    }

    int getCacheSize() {
        return cache.size()
    }

    void purgeCache() {
        log.info "Purging cache"
        cache = new HashMap()
    }

    Result validate(Settings settings, String transaction_ip, String app_name, String repository_name, String module_name, String product_name_space, String version, String email) {
        def validateIpJson = {
            Transaction_IP "${transaction_ip}"
            AppName "${app_name}"
            Email "${email}"
        }

        log.info "Validating IP"
        Result result = validate_cache_internal(transaction_ip, settings.getIpServer(), validateIpJson, STATUS, APPROVED)

        if (result.success()) {
            def validateEntitlementsJson = {
                RepositoryName "${repository_name}"
                ModuleName "${module_name}"
                ProductNameSpace "${product_name_space}"
                Version "${version}"
                Email "${email}"
            }

            log.info "Validating entitlement"
            result = validate_cache_internal(email, settings.getEntitlementServer(), validateEntitlementsJson, STATUS, APPROVED)
        }

        return result;
    }

    private Result validate_cache_internal(String cachekey, String uri, jsonObj, String key, String value) {
        log.debug "Getting item from cache using key: ${cachekey}"
        CacheItem item = cache.get(cachekey)

        if (item != null && item.isValid(jsonObj)) {
            log.debug "Cached item found"
            return item.getResult()
        }

        log.debug "No cached item found."
        cache.remove(cachekey)

        log.debug "Validate with server"
        Result result = validate_internal(uri, jsonObj, key, value)

        log.debug "Caching validation result for key: ${cachekey}"
        item = new CacheItem(jsonObj, result)
        cache.put(cachekey, item)

        return result
    }

    private Result validate_internal(String uri, jsonObj, String key, String value) {
        Result result = new Result(Result.FORBIDDEN, Globals.FAILED)

        try {
            log.info "Validating with server ${uri}"
            this.http.uri = uri

            this.http.request(POST) {
                requestContentType = JSON
                contentType = JSON
                body = JsonOutput.toJson(jsonObj).toString()
                log.warn body

                response.success = { resp, json ->
                    log.info "Received success response from server: ${json}"
                    if (json.containsKey(key) && json.get(key) == value) {
                        log.info Globals.SUCCESS
                        result.setStatus(Result.OK, Globals.SUCCESS)
                    }
                }

                response.failure = { resp, json ->
                    log.info "Received failure response from server: ${json}"
                    log.error Globals.FAILED
                }
            }
        } catch (Exception e) {
            log.warn e.toString()
        }

        log.info "Validation result: ${result.status}"
        return result
    }
}
