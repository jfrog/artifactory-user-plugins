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

import org.artifactory.addon.AddonsManager
import org.artifactory.addon.HaAddon
import org.artifactory.addon.ha.message.HaMessageTopic

/**
 * This plugin will send a notification on CONFIG_CHANGE_TOPIC and ACL_CHANGE_TOPIC
 *
 * To run this plugin use:
 * curl -X GET -v -u {user}:{password} -X POST "http://{Artifactory_IP}/artifactory/api/plugins/execute/syncNotification"
 *
 * @author Michal
 * @since 10/20/14
 */
executions {
    syncNotification(description: 'send HA notifications for permission target and configuration change') {
        AddonsManager addonsManager = ctx.beanForType(AddonsManager.class)

        addonsManager.addonByType(HaAddon.class).notify(HaMessageTopic.CONFIG_CHANGE_TOPIC, null)
        addonsManager.addonByType(HaAddon.class).notify(HaMessageTopic.ACL_CHANGE_TOPIC, null)
    }
}
