/*
 *
 * Copyright 2016 JFrog Ltd. All rights reserved.
 * JFROG PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

import groovy.json.JsonBuilder
import org.artifactory.security.User

/**
 *
 * @author Michal
 * @since 02/04/15
 */

executions {
    getCurrentUserDetails(httpMethod: 'GET', groups:['readers']) {
        log.info "Requesting user details for ${security.currentUsername()}"
        User user = security.currentUser()
        security.populateUserProperties(user)
        JsonBuilder builder = new JsonBuilder(user)
        message = builder.toPrettyString()
        log.debug "Returning User Details: ${message}"
        status = 200
    }
}
