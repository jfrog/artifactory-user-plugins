/*
 * Copyright (C) 2014 JFrog Ltd.
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

import groovy.transform.Field
@Grapes([
    @Grab(group = 'org.codehaus.groovy.modules.http-builder',
          module = 'http-builder', version = '0.7.2')
])
@GrabExclude('commons-codec:commons-codec')
import groovyx.net.http.ContentType
import groovyx.net.http.HTTPBuilder
import groovyx.net.http.Method
import org.artifactory.security.User

/**
 *
 * @author Fred Simon
 * @since 02/04/15
 */

@Field final String centralAuthenticatorArtifactory = 'http://localhost:8081'
@Field final String uriPath = '/artifactory/api/plugins/execute/getCurrentUserDetails'

realms {
    delegateRealm(autoCreateUsers: true) {
        authenticate { username, credentials ->
            if (username == 'anonymous' || username == 'admin') return
            log.info "Authenticating '${username}' against ${centralAuthenticatorArtifactory}/${uriPath} ..."

            def http = new HTTPBuilder(centralAuthenticatorArtifactory)
            http.parser.'application/json' = http.parser.'text/plain'
            http.encoder[ContentType.ANY] = { it }

            boolean passed = false

            try {
                def slurper = new groovy.json.JsonSlurper()
                User currentUser = getUser()
                log.debug "Calling get on getCurrentUserDetails for ${currentUser}"

                http.request(Method.GET, ContentType.TEXT) {
                    uri.path = uriPath
                    headers.'Authorization' = 'Basic ' + "${username}:${credentials}".getBytes().encodeBase64()
                    log.debug "Using ${uri} and ${headers}"
                    response.success = { resp, is ->
                        log.debug "Received data from remote for ${username} with status ${resp.statusLine}"
                        // Populate user from remote details
                        def json = slurper.parse(is)
                        currentUser.privateKey = json.privateKey
                        currentUser.publicKey = json.publicKey
                        currentUser.email = json.email
                        currentUser.groups = json.groups
                        log.info "User ${currentUser.username} was successfully synchronize"
                        passed = true
                    }
                    response.failure = { resp ->
                        if (resp.status == 401) {
                            log.info "User ${username} provided wrong credentials got: ${resp.statusLine}"
                        } else {
                            log.error "Unexpected error while synchronizing ${username} ${resp.statusLine}"
                        }
                    }
                }
            } catch (Throwable th) {
                log.error(th.getMessage(), th)
            }

            return passed
        }

        userExists { username ->
            log.debug "All users may exists even '${username}'"
            true
        }
    }
}
