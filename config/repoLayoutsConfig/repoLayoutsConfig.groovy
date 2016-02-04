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
import org.artifactory.descriptor.repo.RepoLayout
import org.artifactory.resource.ResourceStreamHandle
import org.artifactory.util.AlreadyExistsException

def propList = ['name': [
        CharSequence.class, 'string',
        { c, v -> c.name = v ?: null }
    ], 'artifactPathPattern': [
        CharSequence.class, 'string',
        { c, v -> c.artifactPathPattern = v ?: null }
    ], 'distinctiveDescriptorPathPattern': [
        Boolean.class, 'boolean',
        { c, v -> c.distinctiveDescriptorPathPattern = v ?: false }
    ], 'descriptorPathPattern': [
        CharSequence.class, 'string',
        { c, v -> c.descriptorPathPattern = v ?: null }
    ], 'folderIntegrationRevisionRegExp': [
        CharSequence.class, 'string',
        { c, v -> c.folderIntegrationRevisionRegExp = v ?: null }
    ], 'fileIntegrationRevisionRegExp': [
        CharSequence.class, 'string',
        { c, v -> c.fileIntegrationRevisionRegExp = v ?: null }]]

def validatePathPattern(pattern) {
    if (!pattern.contains('[module]')) return false
    if (!pattern.contains('[baseRev]')) return false
    if (!pattern.contains('[org]') && !pattern.contains('[orgPath]')) {
        return false
    }
    return true
}

executions {
    getLayoutsList(version: '1.0', httpMethod: 'GET') { params ->
        def cfg = ctx.centralConfig.descriptor.repoLayouts
        if (cfg == null) cfg = []
        def json = cfg.collect { it.name }
        message = new JsonBuilder(json).toPrettyString()
        status = 200
    }

    getLayout(version: '1.0', httpMethod: 'GET') { params ->
        def name = params?.get('name')?.get(0) as String
        if (!name) {
            message = 'A layout name is required'
            status = 400
            return
        }
        def layout = ctx.centralConfig.descriptor.getRepoLayout(name)
        if (layout == null) {
            message = "Layout with name '$name' does not exist"
            status = 404
            return
        }
        def json = [
            name: layout.name ?: null,
            artifactPathPattern: layout.artifactPathPattern ?: null,
            distinctiveDescriptorPathPattern:
            layout.isDistinctiveDescriptorPathPattern() ?: false,
            descriptorPathPattern: layout.descriptorPathPattern ?: null,
            folderIntegrationRevisionRegExp:
            layout.folderIntegrationRevisionRegExp ?: null,
            fileIntegrationRevisionRegExp:
            layout.fileIntegrationRevisionRegExp ?: null]
        message = new JsonBuilder(json).toPrettyString()
        status = 200
    }

    deleteLayout(version: '1.0', httpMethod: 'DELETE') { params ->
        def name = params?.get('name')?.get(0) as String
        if (!name) {
            message = 'A layout name is required'
            status = 400
            return
        }
        def cfg = ctx.centralConfig.mutableDescriptor
        def layout = cfg.removeRepoLayout(name)
        if (layout == null) {
            message = "Layout with name '$name' does not exist"
            status = 404
            return
        }
        ctx.centralConfig.saveEditedDescriptorAndReload(cfg)
        status = 200
    }

    addLayout(version: '1.0') { params, ResourceStreamHandle body ->
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
            message = 'A layout name is required'
            status = 400
            return
        }
        if (!(json['name'] ==~ '[_a-zA-Z][-_.a-zA-Z0-9]*')) {
            message = 'Name cannot be blank or contain spaces & special'
            message += ' characters'
            status = 400
            return
        }
        if (!validatePathPattern(json['artifactPathPattern'].toString())) {
            message = 'A valid artifact path pattern is required'
            message += ' (must contain [module], [baseRev],'
            message += ' and [org] or [orgPath], and must not be empty)'
            status = 400
            return
        }
        if (json['distinctiveDescriptorPathPattern'] &&
            !validatePathPattern(json['descriptorPathPattern'].toString())) {
            message = 'The descriptor path pattern must be valid'
            message += ' (must contain [module], [baseRev],'
            message += ' and [org] or [orgPath])'
            status = 400
            return
        }
        if (!json['folderIntegrationRevisionRegExp']) {
            message = 'A folder integration revision regexp is required'
            status = 400
            return
        }
        if (!json['fileIntegrationRevisionRegExp']) {
            message = 'A file integration revision regexp is required'
            status = 400
            return
        }
        def err = null
        def layout = new RepoLayout()
        propList.each { k, v ->
            if (!err && json[k] != null && !(v[0].isInstance(json[k]))) {
                err = "Property '$k' is type"
                err += " '${json[k].getClass().name}',"
                err += " should be a ${v[1]}"
            } else v[2](layout, json[k])
        }
        if (err) {
            message = err
            status = 400
            return
        }
        def cfg = ctx.centralConfig.mutableDescriptor
        try {
            cfg.addRepoLayout(layout)
        } catch (AlreadyExistsException ex) {
            message = "Layout with name '${json['name']}' already exists"
            status = 409
            return
        }
        ctx.centralConfig.saveEditedDescriptorAndReload(cfg)
        status = 200
    }

    updateLayout(version: '1.0') { params, ResourceStreamHandle body ->
        def name = params?.get('name')?.get(0) as String
        if (!name) {
            message = 'A layout name is required'
            status = 400
            return
        }
        def cfg = ctx.centralConfig.mutableDescriptor
        def layout = cfg.getRepoLayout(name)
        if (layout == null) {
            message = "Layout with name '$name' does not exist"
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
                message = 'A layout name must not be empty'
                status = 400
                return
            } else if (!(json['name'] ==~ '[_a-zA-Z][-_.a-zA-Z0-9]*')) {
                message = 'Name cannot be blank or contain spaces & special'
                message += ' characters'
                status = 400
                return
            } else if (json['name'] != name
                       && cfg.isRepoLayoutExists(json['name'])) {
                message = "Layout with name '${json['name']}' already exists"
                status = 409
                return
            }
        }
        if ('artifactPathPattern' in json.keySet() &&
            !validatePathPattern(json['artifactPathPattern'].toString())) {
            message = 'The artifact path pattern must be valid'
            message += ' (must contain [module], [baseRev],'
            message += ' and [org] or [orgPath])'
            status = 400
            return
        }
        if ('descriptorPathPattern' in json.keySet() &&
            ('distinctiveDescriptorPathPattern' in json.keySet() ?
             json['distinctiveDescriptorPathPattern'] :
             layout.isDistinctiveDescriptorPathPattern()) &&
            !validatePathPattern(json['descriptorPathPattern'].toString())) {
            message = 'The descriptor path pattern must be valid'
            message += ' (must contain [module], [baseRev],'
            message += ' and [org] or [orgPath])'
            status = 400
            return
        }
        if ('folderIntegrationRevisionRegExp' in json.keySet() &&
            !json['folderIntegrationRevisionRegExp']) {
            message = 'A folder integration revision regexp must not be empty'
            status = 400
            return
        }
        if ('fileIntegrationRevisionRegExp' in json.keySet() &&
            !json['fileIntegrationRevisionRegExp']) {
            message = 'A file integration revision regexp must not be empty'
            status = 400
            return
        }
        def err = null
        propList.each { k, v ->
            if (!err && k in json.keySet()) {
                if (json[k] && !(v[0].isInstance(json[k]))) {
                    err = "Property '$k' is type"
                    err += " '${json[k].getClass().name}',"
                    err += " should be a ${v[1]}"
                } else v[2](layout, json[k])
            }
        }
        if (err) {
            message = err
            status = 400
            return
        }
        ctx.centralConfig.saveEditedDescriptorAndReload(cfg)
        status = 200
    }
}
