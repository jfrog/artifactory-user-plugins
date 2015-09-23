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

executions {
    getProxiesList(httpMethod: 'GET') { params ->
        def cfg = ctx.centralConfig.descriptor.proxies
        if (cfg == null) cfg = []
        def json = cfg.collect { it.key }
        message = new JsonBuilder(json).toPrettyString()
        status = 200
    }

    getProxy(httpMethod: 'GET') { params ->
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
            key: proxy.key,
            host: proxy.host, port: proxy.port,
            username: proxy.username, password: proxy.password,
            ntHost: proxy.ntHost, domain: proxy.domain,
            defaultProxy: proxy.defaultProxy,
            redirectedToHosts: proxy.redirectedToHostsList]
        message = new JsonBuilder(json).toPrettyString()
        status = 200
    }

    deleteProxy(httpMethod: 'DELETE') { params ->
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
        ctx.centralConfig.descriptor = cfg
        status = 200
    }

    addProxy() { params, ResourceStreamHandle body ->
        def reader = new InputStreamReader(body.inputStream, 'UTF-8')
        def json = null
        try {
            json = new JsonSlurper().parse(reader)
        } catch (groovy.json.JsonException ex) {
            message = "Problem parsing JSON: $ex.message"
            status = 400
            return
        }
        def err = null
        if (!err && !(json instanceof Map)) {
            err = "Provided JSON value must be a JSON object"
        }
        if (!err && !json['key']) {
            err = 'A proxy key is required'
        }
        def proxy = new ProxyDescriptor()
        if (!err && !(json['key'] instanceof CharSequence)) {
            err = "Property 'key' is type"
            err += " '${json['key'].getClass().name}',"
            err += " should be a string"
        } else proxy.key = json['key']
        if (!err && json['host'] != null
            && !(json['host'] instanceof CharSequence)) {
            err = "Property 'host' is type"
            err += " '${json['host'].getClass().name}',"
            err += " should be a string"
        } else proxy.host = json['host'] ?: null
        if (!err && json['port'] != null
            && !(json['port'] instanceof Number)) {
            err = "Property 'port' is type"
            err += " '${json['port'].getClass().name}',"
            err += " should be a integer"
        } else proxy.port = json['port'] ?: 0
        if (!err && json['username'] != null
            && !(json['username'] instanceof CharSequence)) {
            err = "Property 'username' is type"
            err += " '${json['username'].getClass().name}',"
            err += " should be a string"
        } else proxy.username = json['username'] ?: null
        if (!err && json['password'] != null
            && !(json['password'] instanceof CharSequence)) {
            err = "Property 'password' is type"
            err += " '${json['password'].getClass().name}',"
            err += " should be a string"
        } else proxy.password = json['password'] ?: null
        if (!err && json['ntHost'] != null
            && !(json['ntHost'] instanceof CharSequence)) {
            err = "Property 'ntHost' is type"
            err += " '${json['ntHost'].getClass().name}',"
            err += " should be a string"
        } else proxy.ntHost = json['ntHost'] ?: null
        if (!err && json['domain'] != null
            && !(json['domain'] instanceof CharSequence)) {
            err = "Property 'domain' is type"
            err += " '${json['domain'].getClass().name}',"
            err += " should be a string"
        } else proxy.domain = json['domain'] ?: null
        if (!err && json['defaultProxy'] != null
            && !(json['defaultProxy'] instanceof Boolean)) {
            err = "Property 'defaultProxy' is type"
            err += " '${json['defaultProxy'].getClass().name}',"
            err += " should be a boolean"
        } else proxy.defaultProxy = json['defaultProxy'] ?: false
        if (!err && json['redirectedToHosts'] != null
            && !(json['redirectedToHosts'] instanceof Iterable)) {
            err = "Property 'redirectedToHosts' is type"
            err += " '${json['redirectedToHosts'].getClass().name}',"
            err += " should be a list"
        } else proxy.redirectedToHosts = json['redirectedToHosts']?.join(',')
        if (err) {
            message = err
            status = 400
            return
        }
        def cfg = ctx.centralConfig.mutableDescriptor
        cfg.addProxy(proxy, false)
        ctx.centralConfig.descriptor = cfg
        status = 200
    }

    updateProxy() { params, ResourceStreamHandle body ->
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
        def err = null
        if (!err && 'key' in json.keySet()) {
            if (!(json['key'] instanceof CharSequence)) {
                err = "Property 'key' is type"
                err += " '${json['key'].getClass().name}',"
                err += " should be a string"
            } else if (!json['key']) {
                message = 'A proxy key must not be an empty string'
                status = 400
                return
            } else if (json['key'] != key && cfg.isProxyExists(json['key'])) {
                message = "Proxy with key '${json['key']}' already exists"
                status = 409
                return
            } else proxy.key = json['key']
        }
        if (!err && 'host' in json.keySet()) {
            if (!json['host']) json['host'] = null
            if (json['host'] && !(json['host'] instanceof CharSequence)) {
                err = "Property 'host' is type"
                err += " '${json['host'].getClass().name}',"
                err += " should be a string"
            } else proxy.host = json['host']
        }
        if (!err && 'port' in json.keySet()) {
            if (!(json['port'] instanceof Number)) {
                err = "Property 'port' is type"
                err += " '${json['port'].getClass().name}',"
                err += " should be a integer"
            } else proxy.port = json['port']
        }
        if (!err && 'username' in json.keySet()) {
            if (!json['username']) json['username'] = null
            if (json['username']
                && !(json['username'] instanceof CharSequence)) {
                err = "Property 'username' is type"
                err += " '${json['username'].getClass().name}',"
                err += " should be a string"
            } else proxy.username = json['username']
        }
        if (!err && 'password' in json.keySet()) {
            if (!json['password']) json['password'] = null
            if (json['password']
                && !(json['password'] instanceof CharSequence)) {
                err = "Property 'password' is type"
                err += " '${json['password'].getClass().name}',"
                err += " should be a string"
            } else proxy.password = json['password']
        }
        if (!err && 'ntHost' in json.keySet()) {
            if (!json['ntHost']) json['ntHost'] = null
            if (json['ntHost'] && !(json['ntHost'] instanceof CharSequence)) {
                err = "Property 'ntHost' is type"
                err += " '${json['ntHost'].getClass().name}',"
                err += " should be a string"
            } else proxy.ntHost = json['ntHost']
        }
        if (!err && 'domain' in json.keySet()) {
            if (!json['domain']) json['domain'] = null
            if (json['domain'] && !(json['domain'] instanceof CharSequence)) {
                err = "Property 'domain' is type"
                err += " '${json['domain'].getClass().name}',"
                err += " should be a string"
            } else proxy.domain = json['domain']
        }
        if (!err && 'defaultProxy' in json.keySet()) {
            if (!(json['defaultProxy'] instanceof Boolean)) {
                err = "Property 'defaultProxy' is type"
                err += " '${json['defaultProxy'].getClass().name}',"
                err += " should be a boolean"
            } else proxy.defaultProxy = json['defaultProxy']
        }
        if (!err && 'redirectedToHosts' in json.keySet()) {
            if (!(json['redirectedToHosts'] instanceof Iterable)) {
                err = "Property 'redirectedToHosts' is type"
                err += " '${json['redirectedToHosts'].getClass().name}',"
                err += " should be a list"
            } else proxy.redirectedToHosts = json['redirectedToHosts'].join(',')
        }
        if (err) {
            message = err
            status = 400
            return
        }
        cfg.proxyChanged(proxy, true)
        ctx.centralConfig.descriptor = cfg
        status = 200
    }
}
