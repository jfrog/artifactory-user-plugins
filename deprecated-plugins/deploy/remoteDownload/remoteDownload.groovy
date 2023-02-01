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
import groovyx.net.http.HTTPBuilder
import groovyx.net.http.Method
import org.artifactory.repo.RepoPath
import org.artifactory.resource.ResourceStreamHandle

import static groovyx.net.http.ContentType.BINARY
import static org.artifactory.repo.RepoPathFactory.create

/**
 *
 * @author Uriah L.
 * @since 10/05/15
 */

class Params {
    String repo
    String path
    String url
    // Basic authentication credentials for the remote location
    String username
    String password

    boolean isValid() {
        (repo && path && url)
    }
}

executions {
    remoteDownload() { ResourceStreamHandle body ->
        assert body
        def json = new JsonSlurper().parse(new InputStreamReader(body.inputStream))
        def input = new Params()

        input.repo = json.repo as String
        input.path = json.path as String
        input.url = json.url as String
        // need to add validation to whether empty or not
        input.username = json.username as String
        input.password = json.password as String

        if (!input.isValid()) {
            def msg = "One of the mandatory parameters repo, path and url is missing"
            log.warn(msg)
            status = 400
            message = msg
            return
        }

        // Fetch the remote file
        log.info "Fetching remote file from: " + input.url
        // Failure
        if (!fetchAndDeploy(input.url, input.repo, input.path, input.username, input.password)) {
            def msg = "Remote response failure, error code indicated on the log"
            status = 500
            message = msg
            return
        }
    }
}

def boolean fetchAndDeploy(url, repoKey, deployPath, username, password) {
    def http = new HTTPBuilder(url)
    http.auth.basic(username, password)
    // GET request to retrieve remote file
    http.request(Method.GET, BINARY) { req ->
        response.success = { resp, binary ->
            log.info "Got response: ${resp.statusLine}"
            def targetRepoKey = repoKey
            def targetPath = deployPath
            RepoPath deployRepoPath = create(targetRepoKey, targetPath)
            repositories.deploy(deployRepoPath, binary)
        }
        response.failure = { resp ->
            // Can't throw an error to the client from here; returning 0, indicating failure
            log.error "Request failed with the following status code: " + resp.statusLine.statusCode
            return 0
        }
    }
}
