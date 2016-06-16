/*
 *
 * Copyright 2016 JFrog Ltd. All rights reserved.
 * JFROG PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */


@Grapes([
    @Grab(group = 'org.codehaus.groovy.modules.http-builder',
          module = 'http-builder', version = '0.7.2')
])
@GrabExclude('commons-codec:commons-codec')

import groovy.transform.Field
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
                        currentUser.setUserProperty("basictoken", json?.userProperties?.basictoken)
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
