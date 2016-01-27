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
import org.artifactory.descriptor.mail.MailServerDescriptor
import org.artifactory.resource.ResourceStreamHandle

def propList = ['enabled': [
        Boolean.class, 'boolean',
        { c, v -> c.enabled = v ?: false }
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
    ], 'from': [
        CharSequence.class, 'string',
        { c, v -> c.from = v ?: null }
    ], 'subjectPrefix': [
        CharSequence.class, 'string',
        { c, v -> c.subjectPrefix = v ?: null }
    ], 'tls': [
        Boolean.class, 'boolean',
        { c, v -> c.tls = v ?: false }
    ], 'ssl': [
        Boolean.class, 'boolean',
        { c, v -> c.ssl = v ?: false }
    ], 'artifactoryUrl': [
        CharSequence.class, 'string',
        { c, v -> c.artifactoryUrl = v ?: null }]]

executions {
    getSmtp(version: '1.0', httpMethod: 'GET') { params ->
        def cfg = ctx.centralConfig.descriptor.mailServer
        if (cfg == null) cfg = new MailServerDescriptor()
        def json = [
            enabled: cfg.isEnabled() ?: false,
            host: cfg.host ?: null,
            port: cfg.port ?: 0,
            username: cfg.username ?: null,
            password: cfg.password ?: null,
            from: cfg.from ?: null,
            subjectPrefix: cfg.subjectPrefix ?: null,
            tls: cfg.isTls() ?: false,
            ssl: cfg.isSsl() ?: false,
            artifactoryUrl: cfg.artifactoryUrl ?: null]
        message = new JsonBuilder(json).toPrettyString()
        status = 200
    }

    setSmtp(version: '1.0') { params, ResourceStreamHandle body ->
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
        if ('host' in json.keySet() && !json['host']) {
            message = "Property 'host' must not be empty"
            status = 400
            return
        }
        if ('port' in json.keySet() && !(json['port'] instanceof Number) &&
            !json['port']) {
            message = "Property 'port' must not be empty"
            status = 400
            return
        }
        if ('port' in json.keySet() && json['port'] instanceof Number &&
            (json['port'] < 1 || json['port'] > 65535)) {
            message = "Property 'port' must be between 1 and 65535"
            status = 400
            return
        }
        def cfg = ctx.centralConfig.mutableDescriptor
        def smtp = cfg.mailServer
        if (smtp == null) smtp = new MailServerDescriptor()
        def err = null
        propList.each { k, v ->
            if (!err && k in json.keySet()) {
                if (json[k] && !(v[0].isInstance(json[k]))) {
                    err = "Property '$k' is type"
                    err += " '${json[k].getClass().name}',"
                    err += " should be a ${v[1]}"
                } else v[2](smtp, json[k])
            }
        }
        if (err) {
            message = err
            status = 400
            return
        }
        cfg.mailServer = smtp
        ctx.centralConfig.saveEditedDescriptorAndReload(cfg)
        status = 200
    }
}
