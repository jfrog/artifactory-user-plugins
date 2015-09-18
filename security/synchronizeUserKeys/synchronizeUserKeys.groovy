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
    @Grab(group = 'org.codehaus.groovy.modules.http-builder',
          module = 'http-builder', version = '0.6')
])
import groovyx.net.http.HTTPBuilder
import org.apache.http.HttpRequestInterceptor
import org.artifactory.api.security.UserGroupService
import org.artifactory.factory.InfoFactoryHolder
import org.artifactory.repo.RepoPath

import javax.servlet.http.HttpServletRequest

/**
 *
 * @author freds
 * @since 02/28/13
 */

String syncAdminUser = 'admin'
String syncAdminPassword = 'password'

String getOtherHostRootUrl(HttpServletRequest request) {
    def port = request.getServerPort()
    if (port == 8080) 'http://localhost:8081/artifactory'
    else 'http://localhost:8080/artifactory'
}

executions {
    setUserKeys() { params ->
        log.info "Receiving setUSerKeys with ${params}"

        String username = params?.get('username')?.get(0) as String
        String email = params?.get('email')?.get(0) as String
        String privateKey = params?.get('private')?.get(0) as String
        String publicKey = params?.get('public')?.get(0) as String

        if (!params || !username || !privateKey || !publicKey) {
            def errMsg = "Parameters username, private and public are missing please provide them like ?params=username=joe|private=AAA"
            log.error(errMsg)
            status = 400
            message = errMsg
            return
        }

        if (!publicKey.startsWith('AM')) {
            // The 2 equal signs at the end of the public and private keys are removed by the params
            publicKey += '=='
            privateKey += '=='
        }

        // Use the find or create that is used for external LDAP users
        def userService = ctx.beanForType(UserGroupService.class)
        def foundUser = userService.findOrCreateExternalAuthUser(username, false)
        def newUser = InfoFactoryHolder.get().copyUser(foundUser)
        newUser.setEmail(email)
        newUser.setPrivateKey(privateKey)
        newUser.setPublicKey(publicKey)
        userService.updateUser(newUser)

        status = 200
        message = "User $username synchronized sucessfully"
    }
}

download {
    altResponse { request, RepoPath responseRepoPath ->
        if (responseRepoPath.name == 'settings.xml') {
            // Retrieve current user info object
            def userService = ctx.beanForType(UserGroupService.class)
            def currentUser = userService.findUser(security.currentUsername)
            log.info "Downloading a settings.xml => Synchronizing keys for ${currentUser.username}"
            def remoteUrl = "${getOtherHostRootUrl(request.httpRequest)}/api/plugins/execute/setUserKeys"
            // Synchronize the user keys
            def http = new HTTPBuilder(remoteUrl)
            http.client.addRequestInterceptor({ def httpRequest, def httpContext ->
                httpRequest.addHeader('Authorization', "Basic ${"${syncAdminUser}:${syncAdminPassword}".getBytes().encodeBase64()}")
            } as HttpRequestInterceptor)
            http.post(query: [params: "username=${currentUser.username}|email=${currentUser.email}|private=${currentUser.privateKey}|public=${currentUser.publicKey}"]) {
                success = { resp ->
                    log.debug "User ${currentUser.username} was successfully synchronize"
                }
                failure = { resp ->
                    log.error "Unexpected error while synchronizing ${current.username} ${resp}"
                    status = resp.status
                }
            }
        }
    }
}
