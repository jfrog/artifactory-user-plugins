/*
 * Copyright (C) 2015 JFrog Ltd.
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
import java.util.concurrent.TimeUnit
import org.apache.http.HttpEntity
import org.apache.http.HttpResponse
import org.apache.http.client.HttpClient
import org.apache.http.client.HttpResponseException
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpPost
import org.apache.http.client.methods.HttpRequestBase
import org.apache.http.entity.StringEntity
import org.apache.http.util.EntityUtils
import org.artifactory.api.context.ContextHelper
import org.artifactory.common.ConstantValues
import org.artifactory.resource.ResourceStreamHandle
import org.artifactory.rest.resource.system.SystemResource
import org.artifactory.rest.resource.system.VersionResource
import org.artifactory.storage.db.servers.service.ArtifactoryServersCommonService
import org.artifactory.util.HttpClientConfigurator
import org.codehaus.jackson.map.ObjectMapper

executions {
    routedGet(httpMethod: 'GET') { params ->
        def serverId = params?.get('serverId')?.get(0) as String
        def apiEndpoint = params?.get('apiEndpoint')?.get(0) as String
        (message, status) = genericRoutedGet(serverId, apiEndpoint)
    }

    getSystemInfo(httpMethod: 'GET') { params ->
        def serverId = params?.get('serverId')?.get(0) as String
        (message, status) = genericRoutedGet(serverId, "/api/system/info")
    }

    getLicense(httpMethod: 'GET') { params ->
        def serverId = params?.get('serverId')?.get(0) as String
        (message, status) = genericRoutedGet(serverId, "/api/system/license")
    }

    getVersion(httpMethod: 'GET') { params ->
        def serverId = params?.get('serverId')?.get(0) as String
        (message, status) = genericRoutedGet(serverId, "/api/system/version")
    }

    updateLicense(version: '1.0') { params, ResourceStreamHandle body ->
        def bodyText = body.inputStream.text
        def serverId = params?.get('serverId')?.get(0) as String
        (message, status) = genericRoutedPost(serverId, "/api/system/license", bodyText)
    }

}

def genericRoutedGet(String serverId, String apiEndpoint) {
    genericRoutedCall(serverId, apiEndpoint, new HttpGet())
}

def genericRoutedPost(String serverId, String apiEndpoint, String body) {
    def entity = new StringEntity(body)
    entity.setContentType("application/json");

    def post = new HttpPost()
    post.setEntity(entity)

    genericRoutedCall(serverId, apiEndpoint, post)
}

def genericRoutedCall(String serverId, String apiEndpoint, HttpRequestBase base) {
    log.debug("serverId: $serverId; apiEndpoint: $apiEndpoint")

    ArtifactoryServersCommonService sserv = ctx.beanForType(ArtifactoryServersCommonService.class)
    def server = sserv.allArtifactoryServers.find {
        it.artifactoryRunningMode.isHa()
    }

    def targetServer = null

    if (ctx.artifactoryHome.isHaConfigured()) {
        log.info("HA cluster detected.")

        if (serverId) {
            // get server from servers by id
            targetServer = sserv.allArtifactoryServers.find {
                it.artifactoryRunningMode.isHa() && it.serverId == serverId
            }
        } else {
            // url is the current server's url
            targetServer = sserv.currentMember;
        }
    } else {
        return getLocalResource(apiEndpoint)
    }

    if (!targetServer) {
        return ["Server $serverId does not exist", 400]
    }

    def lasthb = System.currentTimeMillis() - targetServer.lastHeartbeat
    def heartbeat = TimeUnit.MILLISECONDS.toSeconds(lasthb)
    if (heartbeat > ConstantValues.haHeartbeatStaleIntervalSecs.getInt()) {
        return ["Server $serverId is unreachable", 400]
    }

    def status = targetServer.serverState.name()
    if (!(status in ['RUNNING', 'STARTING', 'STOPPING', 'CONVERTING'])) {
        return ["Server $serverId is unreachable: status is $status", 400]
    }

    def targeturl = targetServer.contextUrl
    if (!targeturl.startsWith("http://")) {
        targeturl = "http://" + targeturl
    }
    StringBuilder url = new StringBuilder(targeturl)
    if (!server.contextUrl.endsWith("/") && !apiEndpoint.startsWith("/")) {
        url.append("/")
    }

    url.append(apiEndpoint);
    log.debug("Target URL: " + url.toString())

    HttpClient client = null
    try {
        client = createHttpClient(url.toString())
        if (client == null) {
            return
        }

        base.setURI(URI.create(url.toString()));
        def headerValue = org.artifactory.request.RequestThreadLocal.context.get().requestThreadLocal.request.getHeader("authorization")
        if (headerValue == null) {
            return;
        }

        base.addHeader("authorization", headerValue);

        HttpResponse response = client.execute(base);
        String responseBody = EntityUtils.toString(response.getEntity())
        if(response.getStatusLine().statusCode == 400) {
            log.error("Target Server error response: $responseBody")
            message = new JsonSlurper().parseText(responseBody).message
            return [message, response.getStatusLine().getStatusCode()]
        }
        log.debug("Target Server response body: $responseBody")
        return [responseBody, response.getStatusLine().getStatusCode()]
    } catch (IOException ioe) {
        log.error("Target Server error respons: $ioe.message")
        return [ioe.getMessage(), 500]
    } finally {
        client?.close()
    }
}

private HttpClient createHttpClient(String host) {
    org.artifactory.api.security.UserGroupService userGroupService = ContextHelper.get().beanForType(org.artifactory.api.security.UserGroupService.class)

    def user = userGroupService.currentUser()
    if (user != null) {
        return new HttpClientConfigurator()
                .hostFromUrl(host)
                .soTimeout(60000)
                .connectionTimeout(60000)
                .retry(0, false)
                .getClient()
    }
}

def getLocalResource(resource) {
    if (resource == "/api/system/info") {
        def sys = ContextHelper.get().beanForType(SystemResource.class)
        def resp = sys.systemInfo
        def json = new ObjectMapper().writeValueAsString(resp.entity)
        return [json, resp.status]
    } else if (resource == "/api/system/license") {
        def sys = ContextHelper.get().beanForType(SystemResource.class)
        def resp = sys.licenseResource.licenseInfo
        def json = new ObjectMapper().writeValueAsString(resp.entity)
        return [json, resp.status]
    } else if (resource == "/api/system/version") {
        def sys = ContextHelper.get().beanForType(VersionResource.class)
        def json = new ObjectMapper().writeValueAsString(sys.artifactoryVersion)
        return [json, 200]
    }
    return ["Given resource '$resource' is not supported for non-HA.", 400]
}
