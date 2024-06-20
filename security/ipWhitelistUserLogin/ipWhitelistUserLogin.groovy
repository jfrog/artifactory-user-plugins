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

/**
 *
 * @author Stefan Profanter
 * @since 21/04/22
 */
import org.artifactory.security.RealmPolicy

import groovy.json.JsonSlurper
import groovy.transform.Field


@Field final String CONFIG_FILE_PATH = "plugins/ipWhitelistUserLogin.json"
def configFile = new File(ctx.artifactoryHome.etcDir, CONFIG_FILE_PATH)

def config = null

if ( configFile.exists() ) {

    config = new JsonSlurper().parse(configFile.toURL())
    log.info "Loaded ipWhitelistUserLogin config for users: $config.users"

} else {
    log.error "Config file $configFile is missing!"
}


realms {
    ipWhitelistRealm(realmPolicy: RealmPolicy.ADDITIVE) {
        authenticate { username, credentials ->
            if (config == null) {
                log.error "Config file $configFile is missing!"
                return true
            }

            String ip = request.getClientAddress()

            if (!config.users.containsKey(username)) {
                return true
            }

            for (allow_ip in config.users[username]['allow']) {
                if (ip.startsWith("$allow_ip")){
                    if (config.users[username].containsKey("assignGroup")) {
                        String groupName = config.users[username]['assignGroup']
                        log.info "${username} is trying to login from whitelisted IP, assigning the user to ${groupName}"
                        groups += groupName
                    } else {
                        log.info "User: ${username} with IP ${ip} matches allowed IP ${allow_ip}"
                    }
                    return true
                }
            }

            log.warn "User '${username}' login not allowed. IP address ${ip} not whitelisted"
            return false
        }
    }
}

