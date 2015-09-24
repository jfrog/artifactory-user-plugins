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
import org.artifactory.descriptor.security.ldap.LdapSetting
import org.artifactory.descriptor.security.ldap.SearchPattern
import org.artifactory.resource.ResourceStreamHandle
import org.artifactory.util.AlreadyExistsException

def propList = ['key': [
        CharSequence.class, 'string', false,
        { c, v -> c.key = v }
    ], 'enabled': [
        Boolean.class, 'boolean', false,
        { c, v -> c.enabled = v ?: false }
    ], 'ldapUrl': [
        CharSequence.class, 'string', true,
        { c, v -> c.ldapUrl = v ?: null }
    ], 'userDnPattern': [
        CharSequence.class, 'string', true,
        { c, v -> c.userDnPattern = v ?: null }
    ], 'searchFilter': [
        CharSequence.class, 'string', true,
        { c, v -> c.search.searchFilter = v ?: null }
    ], 'searchBase': [
        CharSequence.class, 'string', true,
        { c, v -> c.search.searchBase = v ?: null }
    ], 'searchSubTree': [
        Boolean.class, 'boolean', false,
        { c, v -> c.search.searchSubTree = v ?: false }
    ], 'managerDn': [
        CharSequence.class, 'string', true,
        { c, v -> c.search.managerDn = v ?: null }
    ], 'managerPassword': [
        CharSequence.class, 'string', true,
        { c, v -> c.search.managerPassword = v ?: null }
    ], 'autoCreateUser': [
        Boolean.class, 'boolean', false,
        { c, v -> c.autoCreateUser = v ?: false }
    ], 'emailAttribute': [
        CharSequence.class, 'string', true,
        { c, v -> c.emailAttribute = v ?: null }]]

executions {
    getLdapSettingsList(httpMethod: 'GET') { params ->
        def cfg = ctx.centralConfig.descriptor.security.ldapSettings
        if (cfg == null) cfg = []
        def json = cfg.collect { it.key }
        message = new JsonBuilder(json).toPrettyString()
        status = 200
    }

    getLdapSetting(httpMethod: 'GET') { params ->
        def key = params?.get('key')?.get(0) as String
        if (!key) {
            message = 'A setting key is required'
            status = 400
            return
        }
        def setting = ctx.centralConfig.descriptor.security.getLdapSettings(key)
        if (setting == null) {
            message = "Setting with key '$key' does not exist"
            status = 404
            return
        }
        def json = [
            key: setting.key,
            enabled: setting.isEnabled(),
            ldapUrl: setting.ldapUrl,
            userDnPattern: setting.userDnPattern,
            searchFilter: setting.search.searchFilter,
            searchBase: setting.search.searchBase,
            searchSubTree: setting.search.isSearchSubTree(),
            managerDn: setting.search.managerDn,
            managerPassword: setting.search.managerPassword,
            autoCreateUser: setting.isAutoCreateUser(),
            emailAttribute: setting.emailAttribute]
        message = new JsonBuilder(json).toPrettyString()
        status = 200
    }

    deleteLdapSetting(httpMethod: 'DELETE') { params ->
        def key = params?.get('key')?.get(0) as String
        if (!key) {
            message = 'A setting key is required'
            status = 400
            return
        }
        def cfg = ctx.centralConfig.mutableDescriptor
        def setting = cfg.security.removeLdap(key)
        if (setting == null) {
            message = "Setting with key '$key' does not exist"
            status = 404
            return
        }
        ctx.centralConfig.descriptor = cfg
        status = 200
    }

    addLdapSetting() { params, ResourceStreamHandle body ->
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
            err = 'Provided value must be a JSON object'
        }
        if (!err && !json['key']) {
            err = 'A setting key is required'
        }
        def setting = new LdapSetting()
        setting.search = new SearchPattern()
        propList.each { k, v ->
            if (!err && json[k] != null && !(v[0].isInstance(json[k]))) {
                err = "Property '$k' is type"
                err += " '${json[k].getClass().name}',"
                err += " should be a ${v[1]}"
            } else v[3](setting, json[k])
        }
        if (err) {
            message = err
            status = 400
            return
        }
        def cfg = ctx.centralConfig.mutableDescriptor
        try {
            cfg.security.addLdap(setting)
        } catch (AlreadyExistsException ex) {
            message = "Setting with key '${json['key']}' already exists"
            status = 409
            return
        }
        ctx.centralConfig.descriptor = cfg
        status = 200
    }

    updateLdapSetting() { params, ResourceStreamHandle body ->
        def key = params?.get('key')?.get(0) as String
        if (!key) {
            message = 'A setting key is required'
            status = 400
            return
        }
        def cfg = ctx.centralConfig.mutableDescriptor
        def setting = cfg.security.getLdapSettings(key)
        if (setting == null) {
            message = "Setting with key '$key' does not exist"
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
                message = 'A setting key must not be an empty string'
                status = 400
                return
            } else if (json['key'] != key
                       && cfg.security.isLdapExists(json['key'])) {
                message = "Setting with key '${json['key']}' already exists"
                status = 409
                return
            }
        }
        def err = null
        propList.each { k, v ->
            if (!err && k in json.keySet()) {
                if ((!v[2] || json[k]) && !(v[0].isInstance(json[k]))) {
                    err = "Property '$k' is type"
                    err += " '${json[k].getClass().name}',"
                    err += " should be a ${v[1]}"
                } else v[3](setting, json[k])
            }
        }
        if (err) {
            message = err
            status = 400
            return
        }
        cfg.security.ldapSettingChanged(setting)
        ctx.centralConfig.descriptor = cfg
        status = 200
    }
}
