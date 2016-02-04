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
import org.artifactory.descriptor.repo.ProxyDescriptor
import org.artifactory.resource.ResourceStreamHandle
import org.artifactory.util.AlreadyExistsException

def propList = ['key': [
        CharSequence.class, 'string',
        { c, v -> c.key = v ?: null }
    ], 'host': [
        CharSequence.class, 'string',
        { c, v -> c.host = v ?: null }
    ], 'port': [
        Number.class, 'integer',
        { c, v -> c.port = v ?: 0 }
    ], 'username': [
        CharSequence.class, 'string',
        { c, v -> c.username = v ?: null }
    ], 'password': [
        CharSequence.class, 'string',
        { c, v -> c.password = v ?: null }
    ], 'ntHost': [
        CharSequence.class, 'string',
        { c, v -> c.ntHost = v ?: null }
    ], 'domain': [
        CharSequence.class, 'string',
        { c, v -> c.domain = v ?: null }
    ], 'defaultProxy': [
        Boolean.class, 'boolean',
        { c, v -> c.defaultProxy = v ?: false }
    ], 'redirectedToHosts': [
        CharSequence.class, 'string',
        { c, v -> c.redirectedToHosts = v ?: null }]]

executions {
    getProxiesList(version: '1.0', httpMethod: 'GET') { params ->
        def cfg = ctx.centralConfig.descriptor.proxies
        if (cfg == null) cfg = []
        def json = cfg.collect { it.key }
        message = new JsonBuilder(json).toPrettyString()
        status = 200
    }

    getProxy(version: '1.0', httpMethod: 'GET') { params ->
        def key = params?.get('key')?.get(0) as String
        if (!key) {
            message = 'A proxy key is required'
            status = 400
            return
        }
        def proxy = ctx.centralConfig.descriptor.getProxy(key)
        if (proxy == null) {
            message = "Proxy with key '$key' does not exist"
            status = 404
            return
        }
        def json = [
            key: proxy.key ?: null,
            host: proxy.host ?: null,
            port: proxy.port ?: 0,
            username: proxy.username ?: null,
            password: proxy.password ?: null,
            ntHost: proxy.ntHost ?: null,
            domain: proxy.domain ?: null,
            defaultProxy: proxy.defaultProxy ?: false,
            redirectedToHosts: proxy.redirectedToHosts]
        message = new JsonBuilder(json).toPrettyString()
        status = 200
    }

    deleteProxy(version: '1.0', httpMethod: 'DELETE') { params ->
        def key = params?.get('key')?.get(0) as String
        if (!key) {
            message = 'A proxy key is required'
            status = 400
            return
        }
        def cfg = ctx.centralConfig.mutableDescriptor
        def proxy = cfg.removeProxy(key)
        if (proxy == null) {
            message = "Proxy with key '$key' does not exist"
            status = 404
            return
        }
        ctx.centralConfig.saveEditedDescriptorAndReload(cfg)
        status = 200
    }

    addProxy(version: '1.0') { params, ResourceStreamHandle body ->
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
            message = 'Provided value must be a JSON object'
            status = 400
            return
        }
        if (!json['key']) {
            message = 'A proxy key is required'
            status = 400
            return
        }
        if (!(json['key'] ==~ '[_a-zA-Z][-_.a-zA-Z0-9]*')) {
            message = 'Name cannot be blank or contain spaces & special'
            message += ' characters'
            status = 400
            return
        }
        if (!json['host']) {
            message = 'A proxy host is required'
            status = 400
            return
        }
        if (!json['port']) {
            message = 'A proxy port is required'
            status = 400
            return
        } else if (json['port'] instanceof Number &&
            (json['port'] < 1 || json['port'] > 65535)) {
            message = "Property 'port' must be between 1 and 65535"
            status = 400
            return
        }
        def err = null
        def proxy = new ProxyDescriptor()
        propList.each { k, v ->
            if (!err && json[k] != null && !(v[0].isInstance(json[k]))) {
                err = "Property '$k' is type"
                err += " '${json[k].getClass().name}',"
                err += " should be a ${v[1]}"
            } else v[2](proxy, json[k])
        }
        if (err) {
            message = err
            status = 400
            return
        }
        def cfg = ctx.centralConfig.mutableDescriptor
        try {
            cfg.addProxy(proxy, false)
        } catch (AlreadyExistsException ex) {
            message = "Proxy with key '${json['key']}' already exists"
            status = 409
            return
        }
        ctx.centralConfig.saveEditedDescriptorAndReload(cfg)
        status = 200
    }

    updateProxy(version: '1.0') { params, ResourceStreamHandle body ->
        def key = params?.get('key')?.get(0) as String
        if (!key) {
            message = 'A proxy key is required'
            status = 400
            return
        }
        def cfg = ctx.centralConfig.mutableDescriptor
        def proxy = cfg.getProxy(key)
        if (proxy == null) {
            message = "Proxy with key '$key' does not exist"
            status = 404
            return
        }
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
        if ('key' in json.keySet()) {
            if (!json['key']) {
                message = 'A proxy key must not be empty'
                status = 400
                return
            } else if (!(json['key'] ==~ '[_a-zA-Z][-_.a-zA-Z0-9]*')) {
                message = 'Name cannot be blank or contain spaces & special'
                message += ' characters'
                status = 400
                return
            } else if (json['key'] != key && cfg.isProxyExists(json['key'])) {
                message = "Proxy with key '${json['key']}' already exists"
                status = 409
                return
            }
        }
        if ('host' in json.keySet() && !json['host']) {
            message = 'A proxy host must not be empty'
            status = 400
            return
        }
        if ('port' in json.keySet()) {
            if (!json['port']) {
                message = 'A proxy port must not be empty'
                status = 400
                return
            } else if (json['port'] instanceof Number &&
                (json['port'] < 1 || json['port'] > 65535)) {
                message = "Property 'port' must be between 1 and 65535"
                status = 400
                return
            }
        }
        def err = null
        propList.each { k, v ->
            if (!err && k in json.keySet()) {
                if (json[k] && !(v[0].isInstance(json[k]))) {
                    err = "Property '$k' is type"
                    err += " '${json[k].getClass().name}',"
                    err += " should be a ${v[1]}"
                } else v[2](proxy, json[k])
            }
        }
        if (err) {
            message = err
            status = 400
            return
        }
        cfg.proxyChanged(proxy, true)
        ctx.centralConfig.saveEditedDescriptorAndReload(cfg)
        status = 200
    }
}
