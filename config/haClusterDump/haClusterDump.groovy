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
import org.artifactory.addon.ha.HaCommonAddon
import org.artifactory.state.ArtifactoryServerState
import org.artifactory.storage.db.servers.model.ArtifactoryServer
import org.artifactory.storage.db.servers.service.ArtifactoryServersCommonService

import java.text.SimpleDateFormat

executions {
    haClusterDump(httpMethod: 'GET') { params ->
        def addonsManager = ctx.beanForType(AddonsManager.class)
        HaCommonAddon haCommonAddon = addonsManager.addonByType(HaCommonAddon.class)
        def df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXX")
        ArtifactoryServersCommonService sserv = ctx.beanForType(ArtifactoryServersCommonService.class)
        def curr = sserv.currentMember
        def servers = sserv.allArtifactoryServers.findAll {
            it.artifactoryRunningMode.isHa()
        }
        def members = servers.collect {
            [serverId: it.serverId,
             localMember: curr == it,
             address: "${new URL(it.contextUrl).host}:$it.membershipPort",
             contextUrl: it.contextUrl,
             heartbeat: df.format(new Date(it.lastHeartbeat)),
             serverState: getServerState(haCommonAddon, it),
             serverRole: it.serverRole.prettyName,
             artifactoryVersion: it.artifactoryVersion,
             licenseHash: it.licenseKeyHash]
        }
        def haAddon = addonsManager.addonByType(HaAddon.class)
        def json = [active: haAddon.isHaEnabled(), members: members]
        message = new JsonBuilder(json).toPrettyString()
        status = 200
    }

    getNodeId(httpMethod: 'GET') { params ->
        message = ctx.getServerId()
        status = 200
    }
}

static def getServerState(HaCommonAddon haCommonAddon, ArtifactoryServer server) {
    boolean hasHeartbeat = haCommonAddon.artifactoryServerHasHeartbeat(server)
    if(!hasHeartbeat) {
        return ArtifactoryServerState.UNAVAILABLE.name()
    }
    return server.serverState.name()
}
