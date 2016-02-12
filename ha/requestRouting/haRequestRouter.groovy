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

import org.apache.http.HttpResponse
import org.apache.http.client.HttpClient
import org.apache.http.client.HttpResponseException
import org.apache.http.client.ResponseHandler
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.BasicResponseHandler
import org.artifactory.api.context.ContextHelper
import org.artifactory.common.ArtifactoryHome
import org.artifactory.rest.resource.system.SystemResource
import org.artifactory.rest.resource.system.VersionResource
import org.artifactory.rest.resource.task.TasksResource
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

    getTasks(httpMethod: 'GET') { params ->
        def serverId = params?.get('serverId')?.get(0) as String
        (message, status) = genericRoutedGet(serverId, "/api/tasks")
    }

    getLicense(httpMethod: 'GET') { params ->
        def serverId = params?.get('serverId')?.get(0) as String
        (message, status) = genericRoutedGet(serverId, "/api/system/license")
    }


    getVersion(httpMethod: 'GET') { params ->
        def serverId = params?.get('serverId')?.get(0) as String
        (message, status) = genericRoutedGet(serverId, "/api/system/version")
    }

}

def genericRoutedGet(String serverId, String apiEndpoint) {
    log.error("serverId: $serverId; apiEndpoint: $apiEndpoint")

    ArtifactoryServersCommonService sserv = ctx.beanForType(ArtifactoryServersCommonService.class)
    def server = sserv.allArtifactoryServers.find {
        it.artifactoryRunningMode.isHa()
    }

    def targetServer;

    if (ArtifactoryHome.get().isHaConfigured()) {
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

    StringBuilder url = new StringBuilder(targetServer.contextUrl);
    if (!server.contextUrl.endsWith("/") && !apiEndpoint.startsWith("/")) {
        url.append("/")
    }

    url.append(apiEndpoint);
    log.error("Target URL: " + url.toString())

    HttpClient client = createHttpClient(url.toString())
    if (client == null) {
        return
    }

    HttpGet method = new HttpGet(url.toString());

    def headerValue = org.artifactory.request.RequestThreadLocal.context.get().requestThreadLocal.request.getHeader("authorization")
    if (headerValue == null) {
        return;
    }

    method.addHeader("authorization", headerValue);

    HttpResponse response = client.execute(method);

    try {
        ResponseHandler<String> handler = new BasicResponseHandler()
        String responseBody = handler.handleResponse(response)

        log.error("Target Server response body: $responseBody")
        return [responseBody, response.getStatusLine().getStatusCode()]
    } catch (HttpResponseException e) {
        log.error("Target Server response body: $e.message")
        return [e.getMessage(), response.getStatusLine().getStatusCode()]
    }
}

private HttpClient createHttpClient(String host) {

    org.artifactory.api.security.UserGroupService userGroupService = ContextHelper.get().beanForType(org.artifactory.api.security.UserGroupService.class)

    String currentUsername = userGroupService.currentUsername();

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
    } else if (resource == "/api/tasks") {
        def sys = ContextHelper.get().beanForType(TasksResource.class)
        def json = new ObjectMapper().writeValueAsString(sys.activeTasks)
        return [json, 200]
    }
    return ["Given resource '$resource' is not supported for non-HA.", 400]
}
