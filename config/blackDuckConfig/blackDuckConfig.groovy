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
import org.artifactory.descriptor.external.*
import org.artifactory.descriptor.repo.ProxyDescriptor
import org.artifactory.resource.ResourceStreamHandle

def propList = ['enableIntegration': [
        Boolean.class, 'boolean',
        { c, v -> c.enableIntegration = v ?: false }
    ], 'serverUri': [
        CharSequence.class, 'string',
        { c, v -> c.serverUri = v ?: null }
    ], 'username': [
        CharSequence.class, 'string',
        { c, v -> c.username = v ?: null }
    ], 'password': [
        CharSequence.class, 'string',
        { c, v -> c.password = v ?: null }
    ], 'connectionTimeoutMillis': [
        Number.class, 'integer',
        { c, v -> c.connectionTimeoutMillis = v ?: 0 }
    ], 'proxy': [
        ProxyDescriptor.class, 'string',
        { c, v -> c.proxy = v ?: null }]]

executions {
    getBlackDuck(version: '1.0', httpMethod: 'GET') { params ->
        def ext = ctx.centralConfig.descriptor.externalProvidersDescriptor
        def cfg = ext?.blackDuckSettingsDescriptor
        if (cfg == null) cfg = new BlackDuckSettingsDescriptor()
        def json = [
            enableIntegration: cfg.isEnableIntegration() ?: false,
            serverUri: cfg.serverUri ?: null,
            username: cfg.username ?: null,
            password: cfg.password ?: null,
            connectionTimeoutMillis: cfg.connectionTimeoutMillis ?: 0,
            proxy: cfg.proxy?.key ?: null]
        message = new JsonBuilder(json).toPrettyString()
        status = 200
    }

    setBlackDuck(version: '1.0') { params, ResourceStreamHandle body ->
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
            message = 'Provided JSON value must be a JSON object'
            status = 400
            return
        }
        if ('serverUri' in json.keySet() &&
            !(json['serverUri'] ==~ '^(ftp|https?)://.+$')) {
            message = "Property 'serverUri' must be a valid URL"
            status = 400
            return
        }
        if ('username' in json.keySet() && !json['username']) {
            message = "Property 'username' must not be empty"
            status = 400
            return
        }
        if ('password' in json.keySet() && !json['password']) {
            message = "Property 'password' must not be empty"
            status = 400
            return
        }
        if ('connectionTimeoutMillis' in json.keySet() &&
            !(json['connectionTimeoutMillis'] instanceof Number) &&
            !json['connectionTimeoutMillis']) {
            message = "Property 'connectionTimeoutMillis' must not be empty"
            status = 400
            return
        }
        if ('connectionTimeoutMillis' in json.keySet() &&
            json['connectionTimeoutMillis'] instanceof Number &&
            json['connectionTimeoutMillis'] < 0) {
            message = "Property 'connectionTimeoutMillis' must not"
            message += " be negative"
            status = 400
            return
        }
        def cfg = ctx.centralConfig.mutableDescriptor
        def proxy = cfg.getProxy(json['proxy'])
        if (json['proxy'] && !proxy) {
            message = 'Provided proxy is not an existing configured proxy'
            status = 409
            return
        } else if (json['proxy']) json['proxy'] = proxy
        def ext = cfg.externalProvidersDescriptor
        if (ext == null) ext = new ExternalProvidersDescriptor()
        def bd = ext.blackDuckSettingsDescriptor
        if (bd == null) bd = new BlackDuckSettingsDescriptor()
        def err = null
        propList.each { k, v ->
            if (!err && k in json.keySet()) {
                if (json[k] && !(v[0].isInstance(json[k]))) {
                    err = "Property '$k' is type"
                    err += " '${json[k].getClass().name}',"
                    err += " should be a ${v[1]}"
                } else v[2](bd, json[k])
            }
        }
        if (err) {
            message = err
            status = 400
            return
        }
        cfg.externalProvidersDescriptor = ext
        cfg.externalProvidersDescriptor.blackDuckSettingsDescriptor = bd
        ctx.centralConfig.saveEditedDescriptorAndReload(cfg)
        status = 200
    }
}
