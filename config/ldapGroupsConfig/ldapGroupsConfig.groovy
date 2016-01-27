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
import org.artifactory.descriptor.security.ldap.group.*
import org.artifactory.descriptor.security.ldap.SearchPattern
import org.artifactory.resource.ResourceStreamHandle
import org.artifactory.util.AlreadyExistsException

def propList = ['name': [
        CharSequence.class, 'string',
        { c, v -> c.name = v ?: null }
    ], 'groupBaseDn': [
        CharSequence.class, 'string',
        { c, v -> c.groupBaseDn = v ?: null }
    ], 'filter': [
        CharSequence.class, 'string',
        { c, v -> c.filter = v ?: null }
    ], 'groupNameAttribute': [
        CharSequence.class, 'string',
        { c, v -> c.groupNameAttribute = v ?: null }
    ], 'groupMemberAttribute': [
        CharSequence.class, 'string',
        { c, v -> c.groupMemberAttribute = v ?: null }
    ], 'subTree': [
        Boolean.class, 'boolean',
        { c, v -> c.subTree = v ?: false }
    ], 'descriptionAttribute': [
        CharSequence.class, 'string',
        { c, v -> c.descriptionAttribute = v ?: null }
    ], 'strategy': [
        LdapGroupPopulatorStrategies.class, 'string',
        { c, v -> c.strategy = v ?: LdapGroupPopulatorStrategies.STATIC }
    ], 'enabledLdap': [
        CharSequence.class, 'string',
        { c, v -> c.enabledLdap = v ?: ' ' }]]

executions {
    getLdapGroupsList(version: '1.0', httpMethod: 'GET') { params ->
        def cfg = ctx.centralConfig.descriptor.security.ldapGroupSettings
        if (cfg == null) cfg = []
        def json = cfg.collect { it.name }
        message = new JsonBuilder(json).toPrettyString()
        status = 200
    }

    getLdapGroup(version: '1.0', httpMethod: 'GET') { params ->
        def name = params?.get('name')?.get(0) as String
        if (!name) {
            message = 'A group name is required'
            status = 400
            return
        }
        def security = ctx.centralConfig.descriptor.security
        def group = security.getLdapGroupSettings(name)
        if (group == null) {
            message = "Group with name '$name' does not exist"
            status = 404
            return
        }
        def strats = [
            (LdapGroupPopulatorStrategies.HIERARCHICAL): 'HIERARCHICAL',
            (LdapGroupPopulatorStrategies.STATIC): 'STATIC',
            (LdapGroupPopulatorStrategies.DYNAMIC): 'DYNAMIC']
        def json = [
            name: group.name ?: null,
            groupBaseDn: group.groupBaseDn ?: null,
            groupNameAttribute: group.groupNameAttribute ?: null,
            filter: group.filter ?: null,
            groupMemberAttribute: group.groupMemberAttribute ?: null,
            subTree: group.isSubTree() ?: false,
            descriptionAttribute: group.descriptionAttribute ?: null,
            strategy: strats[group.strategy],
            enabledLdap: group.enabledLdap ?: null]
        if (json['enabledLdap'] ==~ '\\s+') json['enabledLdap'] = null
        message = new JsonBuilder(json).toPrettyString()
        status = 200
    }

    deleteLdapGroup(version: '1.0', httpMethod: 'DELETE') { params ->
        def name = params?.get('name')?.get(0) as String
        if (!name) {
            message = 'A group name is required'
            status = 400
            return
        }
        def cfg = ctx.centralConfig.mutableDescriptor
        def group = cfg.security.removeLdapGroup(name)
        if (group == null) {
            message = "Group with name '$name' does not exist"
            status = 404
            return
        }
        ctx.centralConfig.saveEditedDescriptorAndReload(cfg)
        status = 200
    }

    addLdapGroup(version: '1.0') { params, ResourceStreamHandle body ->
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
        if (!json['name']) {
            message = 'A group name is required'
            status = 400
            return
        }
        if (!json['groupMemberAttribute']) {
            message = 'A group member attribute is required'
            status = 400
            return
        }
        if (!json['groupNameAttribute']) {
            message = 'A group name attribute is required'
            status = 400
            return
        }
        if (!json['descriptionAttribute']) {
            message = 'A description attribute is required'
            status = 400
            return
        }
        if (!json['filter']) {
            message = 'A filter is required'
            status = 400
            return
        }
        def cfg = ctx.centralConfig.mutableDescriptor
        if (json['enabledLdap']) {
            def enabledLdap = cfg.security.getLdapSettings(json['enabledLdap'])
            if (enabledLdap == null) {
                message = "Setting with key '${json['enabledLdap']}' specified"
                message += " by property 'enabledLdap' does not exist"
                status = 409
                return
            }
        }
        def strats = [
            'HIERARCHICAL': LdapGroupPopulatorStrategies.HIERARCHICAL,
            'STATIC': LdapGroupPopulatorStrategies.STATIC,
            'DYNAMIC': LdapGroupPopulatorStrategies.DYNAMIC]
        if (!json['strategy']) json['strategy'] = 'STATIC'
        if (json['strategy'].toString().toUpperCase() in strats) {
            json['strategy'] = strats[json['strategy']]
        } else {
            def err = "Invalid value '${json['strategy']}' for"
            err += " property 'strategy': must be 'STATIC', "
            err += "'DYNAMIC', or 'HIERARCHICAL'"
            message = err
            status = 400
            return
        }
        def err = null
        def group = new LdapGroupSetting()
        propList.each { k, v ->
            if (!err && json[k] != null && !(v[0].isInstance(json[k]))) {
                err = "Property '$k' is type"
                err += " '${json[k].getClass().name}',"
                err += " should be a ${v[1]}"
            } else v[2](group, json[k])
        }
        if (err) {
            message = err
            status = 400
            return
        }
        try {
            cfg.security.addLdapGroup(group)
        } catch (AlreadyExistsException ex) {
            message = "Group with name '${json['name']}' already exists"
            status = 409
            return
        }
        ctx.centralConfig.saveEditedDescriptorAndReload(cfg)
        status = 200
    }

    updateLdapGroup(version: '1.0') { params, ResourceStreamHandle body ->
        def name = params?.get('name')?.get(0) as String
        if (!name) {
            message = 'A group name is required'
            status = 400
            return
        }
        def cfg = ctx.centralConfig.mutableDescriptor
        def group = cfg.security.getLdapGroupSettings(name)
        if (group == null) {
            message = "Group with name '$name' does not exist"
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
        if ('name' in json.keySet()) {
            if (!json['name']) {
                message = 'A group name must not be empty'
                status = 400
                return
            } else if (json['name'] != name
                       && cfg.security.getLdapGroupSettings(json['name'])) {
                message = "Group with name '${json['name']}' already exists"
                status = 409
                return
            }
        }
        if ('groupMemberAttribute' in json.keySet()
            && !json['groupMemberAttribute']) {
            message = 'A group member attribute must not be empty'
            status = 400
            return
        }
        if ('groupNameAttribute' in json.keySet()
            && !json['groupNameAttribute']) {
            message = 'A group name attribute must not be empty'
            status = 400
            return
        }
        if ('descriptionAttribute' in json.keySet()
            && !json['descriptionAttribute']) {
            message = 'A description attribute must not be empty'
            status = 400
            return
        }
        if ('filter' in json.keySet() && !json['filter']) {
            message = 'A filter must not be empty'
            status = 400
            return
        }
        if ('enabledLdap' in json.keySet()) {
            def enabledLdap = cfg.security.getLdapSettings(json['enabledLdap'])
            if (enabledLdap == null) {
                message = "Setting with key '${json['enabledLdap']}' specified"
                message += " by property 'enabledLdap' does not exist"
                status = 409
                return
            }
        }
        def strats = [
            'HIERARCHICAL': LdapGroupPopulatorStrategies.HIERARCHICAL,
            'STATIC': LdapGroupPopulatorStrategies.STATIC,
            'DYNAMIC': LdapGroupPopulatorStrategies.DYNAMIC]
        if (json['strategy']) {
            if (json['strategy'].toString().toUpperCase() in strats) {
                json['strategy'] = strats[json['strategy']]
            } else {
                def err = "Invalid value '${json['strategy']}' for"
                err += " property 'strategy': must be 'STATIC', "
                err += "'DYNAMIC', or 'HIERARCHICAL'"
                message = err
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
                } else v[2](group, json[k])
            }
        }
        if (err) {
            message = err
            status = 400
            return
        }
        cfg.security.ldapGroupSettingChanged(group)
        ctx.centralConfig.saveEditedDescriptorAndReload(cfg)
        status = 200
    }
}
