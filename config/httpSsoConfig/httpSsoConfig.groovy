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
import groovy.json.JsonSlurper
import org.artifactory.descriptor.security.sso.HttpSsoSettings
import org.artifactory.resource.ResourceStreamHandle

def propList = ['httpSsoProxied': [
        Boolean.class, 'boolean',
        { c, v -> c.httpSsoProxied = v ?: false }
    ], 'noAutoUserCreation': [
        Boolean.class, 'boolean',
        { c, v -> c.noAutoUserCreation = v ?: false }
    ], 'remoteUserRequestVariable': [
        CharSequence.class, 'string',
        { c, v -> c.remoteUserRequestVariable = v ?: null }]]

executions {
    getHttpSso(version: '1.0', httpMethod: 'GET') { params ->
        def cfg = ctx.centralConfig.descriptor.security.httpSsoSettings
        if (cfg == null) cfg = new HttpSsoSettings()
        def json = [
            httpSsoProxied: cfg.isHttpSsoProxied() ?: false,
            noAutoUserCreation: cfg.isNoAutoUserCreation() ?: false,
            remoteUserRequestVariable: cfg.remoteUserRequestVariable ?: null]
        message = new JsonBuilder(json).toPrettyString()
        status = 200
    }

    setHttpSso(version: '1.0') { params, ResourceStreamHandle body ->
        def reader = new InputStreamReader(body.inputStream, 'UTF-8')
        def json = null
        try {
            json = new JsonSlurper().parse(reader)
        } catch (groovy.json.JsonException ex) {
            message = "Problem parsing JSON: $ex.message"
            status = 400
            return
        }
        if (!(json instanceof Map)) {
            message = "Provided JSON value must be a JSON object"
            status = 400
            return
        }
        def cfg = ctx.centralConfig.mutableDescriptor
        def httpSso = cfg.security.httpSsoSettings
        if (httpSso == null) httpSso = new HttpSsoSettings()
        def err = null
        propList.each { k, v ->
            if (!err && k in json.keySet()) {
                if (json[k] && !(v[0].isInstance(json[k]))) {
                    err = "Property '$k' is type"
                    err += " '${json[k].getClass().name}',"
                    err += " should be a ${v[1]}"
                } else v[2](httpSso, json[k])
            }
        }
        if (err) {
            message = err
            status = 400
            return
        }
        cfg.security.httpSsoSettings = httpSso
        ctx.centralConfig.saveEditedDescriptorAndReload(cfg)
        status = 200
    }
}
