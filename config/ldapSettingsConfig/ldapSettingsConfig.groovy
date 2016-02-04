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

def rexurl = '^(?:(?:[lL][dD][aA][pP][sS]?)://)(?:\\S+(?::\\S*)?@)?(?:(?!10(' +
    '?:\\.\\d{1,3}){3})(?!127(?:\\.\\d{1,3}){3})(?!169\\.254(?:\\.\\d{1,3}){' +
    '2})(?!192\\.168(?:\\.\\d{1,3}){2})(?!172\\.(?:1[6-9]|2\\d|3[0-1])(?:\\.' +
    '\\d{1,3}){2})(?:[1-9]\\d?|1\\d\\d|2[01]\\d|22[0-3])(?:\\.(?:1?\\d{1,2}|' +
    '2[0-4]\\d|25[0-5])){2}(?:\\.(?:[1-9]\\d?|1\\d\\d|2[0-4]\\d|25[0-4]))|(?' +
    ':(?:[a-zA-Z\\u00a1-\\uffff0-9]+-?)*[a-zA-Z\\u00a1-\\uffff0-9]+)(?:\\.(?' +
    ':[a-zA-Z\\u00a1-\\uffff0-9]+-?)*[a-zA-Z\\u00a1-\\uffff0-9]+)*(?:.(?:[a-' +
    'zA-Z\\u00a1-\\uffff]{0,})))(?::\\d{2,5})?(?:/[^\\s]*)?$'

def propList = ['key': [
        CharSequence.class, 'string',
        { c, v -> c.key = v ?: null }
    ], 'enabled': [
        Boolean.class, 'boolean',
        { c, v -> c.enabled = v ?: false }
    ], 'ldapUrl': [
        CharSequence.class, 'string',
        { c, v -> c.ldapUrl = v ?: null }
    ], 'userDnPattern': [
        CharSequence.class, 'string',
        { c, v -> c.userDnPattern = v ?: null }
    ], 'searchFilter': [
        CharSequence.class, 'string',
        { c, v -> c.search.searchFilter = v ?: null }
    ], 'searchBase': [
        CharSequence.class, 'string',
        { c, v -> c.search.searchBase = v ?: null }
    ], 'searchSubTree': [
        Boolean.class, 'boolean',
        { c, v -> c.search.searchSubTree = v ?: false }
    ], 'managerDn': [
        CharSequence.class, 'string',
        { c, v -> c.search.managerDn = v ?: null }
    ], 'managerPassword': [
        CharSequence.class, 'string',
        { c, v -> c.search.managerPassword = v ?: null }
    ], 'autoCreateUser': [
        Boolean.class, 'boolean',
        { c, v -> c.autoCreateUser = v ?: false }
    ], 'emailAttribute': [
        CharSequence.class, 'string',
        { c, v -> c.emailAttribute = v ?: null }]]

executions {
    getLdapSettingsList(version: '1.0', httpMethod: 'GET') { params ->
        def cfg = ctx.centralConfig.descriptor.security.ldapSettings
        if (cfg == null) cfg = []
        def json = cfg.collect { it.key }
        message = new JsonBuilder(json).toPrettyString()
        status = 200
    }

    getLdapSetting(version: '1.0', httpMethod: 'GET') { params ->
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
            key: setting.key ?: null,
            enabled: setting.isEnabled() ?: false,
            ldapUrl: setting.ldapUrl ?: null,
            userDnPattern: setting.userDnPattern ?: null,
            searchFilter: setting.search.searchFilter ?: null,
            searchBase: setting.search.searchBase ?: null,
            searchSubTree: setting.search.isSearchSubTree() ?: false,
            managerDn: setting.search.managerDn ?: null,
            managerPassword: setting.search.managerPassword ?: null,
            autoCreateUser: setting.isAutoCreateUser() ?: false,
            emailAttribute: setting.emailAttribute ?: null]
        message = new JsonBuilder(json).toPrettyString()
        status = 200
    }

    deleteLdapSetting(version: '1.0', httpMethod: 'DELETE') { params ->
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
        ctx.centralConfig.saveEditedDescriptorAndReload(cfg)
        status = 200
    }

    addLdapSetting(version: '1.0') { params, ResourceStreamHandle body ->
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
            message = 'A setting key is required'
            status = 400
            return
        }
        if (!(json['key'] ==~ '[_:a-zA-Z][-._:a-zA-Z0-9]*')) {
            message = 'Name cannot be blank or contain spaces & special'
            message += ' characters'
            status = 400
            return
        }
        if (!json['ldapUrl']) {
            message = 'An LDAP URL is required'
            status = 400
            return
        }
        if (!(json['ldapUrl'] ==~ rexurl)) {
            message = 'The LDAP URL must be a valid LDAP URL'
            status = 400
            return
        }
        if (!json['userDnPattern'] && !json['searchFilter']) {
            message = 'LDAP settings should provide a userDnPattern'
            message += ' or a searchFilter (or both)'
            status = 400
            return
        }
        def err = null
        def setting = new LdapSetting()
        setting.search = new SearchPattern()
        propList.each { k, v ->
            if (!err && json[k] != null && !(v[0].isInstance(json[k]))) {
                err = "Property '$k' is type"
                err += " '${json[k].getClass().name}',"
                err += " should be a ${v[1]}"
            } else v[2](setting, json[k])
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
        ctx.centralConfig.saveEditedDescriptorAndReload(cfg)
        status = 200
    }

    updateLdapSetting(version: '1.0') { params, ResourceStreamHandle body ->
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
                message = 'A setting key must not be empty'
                status = 400
                return
            } else if (!(json['key'] ==~ '[_:a-zA-Z][-._:a-zA-Z0-9]*')) {
                message = 'Name cannot be blank or contain spaces & special'
                message += ' characters'
                status = 400
                return
            } else if (json['key'] != key
                       && cfg.security.isLdapExists(json['key'])) {
                message = "Setting with key '${json['key']}' already exists"
                status = 409
                return
            }
        }
        if ('ldapUrl' in json.keySet()) {
            if (!json['ldapUrl']) {
                message = 'An LDAP URL must not be empty'
                status = 400
                return
            }
            if (!(json['ldapUrl'] ==~ rexurl)) {
                message = 'The LDAP URL must be a valid LDAP URL'
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
                } else v[2](setting, json[k])
            }
        }
        if (err) {
            message = err
            status = 400
            return
        }
        cfg.security.ldapSettingChanged(setting)
        ctx.centralConfig.saveEditedDescriptorAndReload(cfg)
        status = 200
    }
}
