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

import groovy.json.JsonBuilder

/**
 *
 * @author Michal
 * @since 02/04/15
 */

executions {
    getCurrentUserDetails(httpMethod: 'GET', groups: ['readers']) {
        log.info "Requesting user details for ${security.currentUsername()}"
        JsonBuilder builder = new JsonBuilder(security.currentUser())
        message = builder.toPrettyString()
        log.debug "Returning User Details: ${message}"
        status = 200
    }
}
