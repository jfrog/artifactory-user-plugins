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

import groovy.json.JsonBuilder
import org.artifactory.addon.AddonsManager
import org.artifactory.addon.HaAddon
import org.artifactory.storage.db.servers.service.ArtifactoryServersCommonService

import java.text.SimpleDateFormat

executions {
    haClusterDump(httpMethod: 'GET') { params ->
        def df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXX")
        ArtifactoryServersCommonService sserv = ctx.beanForType(ArtifactoryServersCommonService.class)
        def curr = sserv.currentMember
        def servers = sserv.allArtifactoryServers.findAll {
            it.artifactoryRunningMode.isHa()
        }
        def members = servers.collect {
            [serverId: it.serverId,
             localMember: curr == it,
             address: "$it.contextUrl:$it.membershipPort",
             heartbeat: df.format(new Date(it.lastHeartbeat)),
             serverState: it.serverState.name(),
             serverRole: it.serverRole.prettyName,
             artifactoryVersion: it.artifactoryVersion]
        }
        def addonsManager = ctx.beanForType(AddonsManager.class)
        def haAddon = addonsManager.addonByType(HaAddon.class)
        def json = [active: haAddon.isHaEnabled(), members: members]
        message = new JsonBuilder(json).toPrettyString()
        status = 200
    }
}
