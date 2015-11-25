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

import org.artifactory.api.security.UserGroupService
import groovy.json.JsonBuilder

/**
 * This plugin finds all the users that are included in a specific group
 *
 * @author Alexei Vainshtein
 * @since 07/21/15
 */

/**
 * The command to get the info:
 * curl -X GET -u{admin_user}:{password} "http://{ARTIFACTORY_URL}:{PORT}/artifactory/api/plugins/execute/getUsersByGroup?params=group={group_name}"
 * for example: curl -X GET -uadmin:password "http://localhost:8081/artifactory/api/plugins/execute/getUsersByGroup?params=group=readers"
 */
executions {
    getUsersByGroup(httpMethod: 'GET') { params ->
        def userGroupService = ctx.beanForType(UserGroupService.class)
        def groupName = params['group']?.get(0)
        log.info("Getting the users of the group '$groupName'")
        // if a group was not specified, return 400
        if (groupName == null) {
            status = 400
            message = "A group must be specified"
            log.error("A group was not specified")
            return
        }
        // if the group does not exist, return 404
        if (userGroupService.findGroup(groupName) == null) {
            status = 404
            message = "No such group '$groupName'"
            log.error("Group '$groupName' does not exist")
            return
        }
        // get the list of users who are in the group
        def users = []
        def usersInGroup = userGroupService.findUsersInGroup(groupName)
        for (def user : usersInGroup) users.add([name: user.username])
        // print the message to the requesting user
        message = new JsonBuilder([groupName, users]).toPrettyString()
        log.info("Finished getting the users of the group '$groupName'")
    }
}
