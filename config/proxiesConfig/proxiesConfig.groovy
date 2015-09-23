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
        def json = new JsonSlurper().parse(reader)
        if (!json['key']?.length()) {
            message = "A proxy key is required"
            status = 400
            return
        }
        def proxy = new ProxyDescriptor()
        proxy.key = json['key']
        proxy.host = json['host']
        proxy.port = json['port'] ?: 0
        proxy.username = json['username']
        proxy.password = json['password']
        proxy.ntHost = json['ntHost']
        proxy.domain = json['domain']
        proxy.defaultProxy = json['defaultProxy'] ?: false
        proxy.redirectedToHosts = json['redirectedToHosts']?.join(',')
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
        def json = new JsonSlurper().parse(reader)
        if (json['key'] && json['key'].length() == 0) {
            message = "A proxy key must not be empty"
            status = 400
            return
        }
        if ('key' in json.keySet()) proxy.key = json['key']
        if ('host' in json.keySet()) proxy.host = json['host']
        if ('port' in json.keySet()) proxy.port = json['port']
        if ('username' in json.keySet()) proxy.username = json['username']
        if ('password' in json.keySet()) proxy.password = json['password']
        if ('ntHost' in json.keySet()) proxy.ntHost = json['ntHost']
        if ('domain' in json.keySet()) proxy.domain = json['domain']
        if ('defaultProxy' in json.keySet()) {
            proxy.defaultProxy = json['defaultProxy']
        }
        if ('redirectedToHosts' in json.keySet()) {
            proxy.redirectedToHosts = json['redirectedToHosts'].join(',')
        }
        cfg.proxyChanged(proxy, true)
        ctx.centralConfig.descriptor = cfg
        status = 200
    }
}
