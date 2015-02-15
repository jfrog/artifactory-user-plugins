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

@Grapes([
        @Grab(group = 'org.codehaus.groovy.modules.http-builder', module = 'http-builder', version = '0.6')
]) @GrabExclude('commons-codec:commons-codec')

import groovyx.net.http.ContentType
import groovyx.net.http.HTTPBuilder
import org.apache.http.HttpRequestInterceptor
import org.artifactory.security.User

/**
 *
 * Date: 2/4/15
  * @author Michal
 */

String centralAuthenticatorArtifactory = 'http://artifactory.inc.jfrog.local:8081/artifactory'


realms {
    delegateRealm(autoCreateUsers: true) {
        authenticate { username, credentials ->
            if (username == 'anonymous' || username == 'admin') return
            log.info "Authenticating '${username}/${credentials}' against $centralAuthenticatorArtifactory ..."

            def http = new HTTPBuilder("${centralAuthenticatorArtifactory}/")
            http.client.addRequestInterceptor({ def httpRequest, def httpContext ->
                httpRequest.addHeader('Authorization', "Basic ${"${username}:${credentials}".getBytes().encodeBase64()}")
            } as HttpRequestInterceptor)
            http.parser.'application/json' = http.parser.'text/plain'
            http.encoder[ContentType.ANY] = { it }

            http.get(path: 'api/plugins/execute/getCurrentUserDetails' ) {
                success = { resp, json ->
                    // Populate user from remote details
                    User currentUser = getUser()
                    currentUser.privateKey = json.privateKey
                    currentUser.publicKey = json.publicKey
                    currentUser.email = json.email
                    currentUser.groups = json.groups
                    log.info "User ${currentUser.username} was successfully synchronize"
                }
                failure = { resp ->
                    log.error "Unexpected error while synchronizing ${current.username} ${resp}"
                    status = resp.status
                }
            }
            true
        }

        userExists { username ->
            log.debug "All users may exists even '${username}'"
            true
        }
    }
}


