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

import org.artifactory.repo.RepoPath
import org.artifactory.request.Request

import static com.google.common.collect.Multimaps.forMap
import static org.jfrog.build.api.BlackDuckPropertiesFields.APP_NAME
import static org.jfrog.build.api.BlackDuckPropertiesFields.APP_VERSION
import static org.jfrog.build.api.BuildInfoProperties.BUILD_INFO_BLACK_DUCK_PROPERTIES_PREFIX

/**
 * Prevent the download of all rejected code center artifact for specific code center application
 *
 * @author michal
 * @since 12/11/14
 */

final CC_PROP_NAME_PREFIX = 'blackduck.cc'
final ID_PROP_NAME = CC_PROP_NAME_PREFIX + '.id'
final EXTERNALID_PROP_NAME = CC_PROP_NAME_PREFIX + '.externalid'
final APP_NAME_MATRIX_PARAM = BUILD_INFO_BLACK_DUCK_PROPERTIES_PREFIX + APP_NAME
final APP_VERSION_MATRIX_PARAM = BUILD_INFO_BLACK_DUCK_PROPERTIES_PREFIX + APP_VERSION

download {
    altResponse { Request request, RepoPath responseRepoPath ->
        // extract the appName and appVersion
        def appName = request.properties.getFirst(APP_NAME_MATRIX_PARAM)
        def appVersion = request.properties.getFirst(APP_VERSION_MATRIX_PARAM)
        // If no name or version skip => do nothing
        if (appName && appVersion) {
            def artifactStatus = repositories.getProperties(responseRepoPath).getFirst(CC_PROP_NAME_PREFIX + '.' + appName + '.' + appVersion + '.rejected.timestamp')
            if (artifactStatus) {
                status = 403
                message = 'This artifact was rejected by cc.'
                log.warn "You asked for an unapproved artifact: $responseRepoPath. 403 in da face!"
            }
        }
    }
}

executions {
    // The BD CC calls the REST API /api/plugins/execute/setStatusProperty?params=id=459201|externalId=:Newtonsoft.Json:5.0.6|appName=demo|appVersion=1.0|status=rejected
    setCCProperty(version: '1.0',
        description: 'set a new status value on all files with CodeCenter id or externalId provided',
        httpMethod: 'POST', users: ['blackduck'].toSet(),
        params: [id: '', externalid: '', appName: '', appVersion: '', status: 'rejected']) { params ->
        String id = params?.get('id')?.get(0)
        String externalId = params?.get('externalId')?.get(0)
        String appName = params?.get('appName')?.get(0)
        String appVersion = params?.get('appVersion')?.get(0)
        String ccStatus = params?.get('status')?.get(0)
        log.debug "trying to change cc status of id=$id or externalId=$externalId with status=$ccStatus"
        if (!id && !externalId) {
            status = 400
            message = "A BlackDuck CodeCenter id or externalId is needed to set the new status!"
            return
        }
        if (!ccStatus) {
            status = 400
            message = "No value for the new status was provided!"
            return
        }
        if (!appVersion || !appName) {
            status = 400
            message = "No value for the appName or AppVersion was provided!"
            return
        }

        def filter = [:]
        if (id) {
            filter.put(ID_PROP_NAME, id)
        } else {
            if (externalId.contains("%")) {
                externalId = org.artifactory.util.HttpUtils.decodeUri(externalId)
            }
            filter.put(EXTERNALID_PROP_NAME, externalId)
        }
        List<RepoPath> found = searches.itemsByProperties(forMap(filter))
        if (!found) {
            status = 404
            message = "No artifacts found with id=$id or externalId=$externalId"
            return
        }
        String propValue = System.currentTimeMillis()
        List<String> results = ['Converted BEGIN']
        found.each { RepoPath repoPath ->
            log.debug "Setting ${CC_PROP_NAME_PREFIX}.${appName}.${appVersion}.${ccStatus}.timestamp on ${repoPath.getId()}"
            String propertyName = CC_PROP_NAME_PREFIX + '.' + appName + '.' + appVersion + '.' + ccStatus + '.timestamp'
            repositories.setProperty(repoPath, propertyName, propValue)
            results << "${repoPath.getId()}:$ccStatus"
        }
        results << 'Converted END\n'
        status = 200
        message = results.join("\n")
    }
}
