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
import org.artifactory.descriptor.backup.BackupDescriptor
import org.artifactory.resource.ResourceStreamHandle
import org.artifactory.util.AlreadyExistsException

def propList = ['key': [
        CharSequence.class, 'string',
        { c, v -> c.key = v ?: null }
    ], 'enabled': [
        Boolean.class, 'boolean',
        { c, v -> c.enabled = v ?: false }
    ], 'dir': [
        File.class, 'string',
        { c, v -> c.dir = v ?: null }
    ], 'cronExp': [
        CharSequence.class, 'string',
        { c, v -> c.cronExp = v ?: null }
    ], 'retentionPeriodHours': [
        Number.class, 'integer',
        { c, v -> c.retentionPeriodHours = v ?: 0 }
    ], 'createArchive': [
        Boolean.class, 'boolean',
        { c, v -> c.createArchive = v ?: false }
    ], 'excludedRepositories': [
        Iterable.class, 'list',
        { c, v -> c.excludedRepositories = v ?: [] }
    ], 'sendMailOnError': [
        Boolean.class, 'boolean',
        { c, v -> c.sendMailOnError = v ?: false }
    ], 'excludeBuilds': [
        Boolean.class, 'boolean',
        { c, v -> c.excludeBuilds = v ?: false }
    ], 'excludeNewRepositories': [
        Boolean.class, 'boolean',
        { c, v -> c.excludeNewRepositories = v ?: false }]]

executions {
    getBackupsList(httpMethod: 'GET') { params ->
        def cfg = ctx.centralConfig.descriptor.backups
        if (cfg == null) cfg = []
        def json = cfg.collect { it.key }
        message = new JsonBuilder(json).toPrettyString()
        status = 200
    }

    getBackup(httpMethod: 'GET') { params ->
        def key = params?.get('key')?.get(0) as String
        if (!key) {
            message = 'A backup key is required'
            status = 400
            return
        }
        def backup = ctx.centralConfig.descriptor.getBackup(key)
        if (backup == null) {
            message = "Backup with key '$key' does not exist"
            status = 404
            return
        }
        def exrepos = backup.excludedRepositories?.collect { it.key }
        def json = [
            key: backup.key ?: null,
            enabled: backup.isEnabled() ?: false,
            dir: backup.dir?.canonicalPath ?: null,
            cronExp: backup.cronExp ?: null,
            retentionPeriodHours: backup.retentionPeriodHours ?: 0,
            createArchive: backup.isCreateArchive() ?: false,
            excludedRepositories: exrepos ?: null,
            sendMailOnError: backup.isSendMailOnError() ?: false,
            excludeBuilds: backup.isExcludeBuilds() ?: false,
            excludeNewRepositories: backup.isExcludeNewRepositories() ?: false]
        message = new JsonBuilder(json).toPrettyString()
        status = 200
    }

    deleteBackup(httpMethod: 'DELETE') { params ->
        def key = params?.get('key')?.get(0) as String
        if (!key) {
            message = 'A backup key is required'
            status = 400
            return
        }
        def cfg = ctx.centralConfig.mutableDescriptor
        def backup = cfg.removeBackup(key)
        if (backup == null) {
            message = "Backup with key '$key' does not exist"
            status = 404
            return
        }
        ctx.centralConfig.descriptor = cfg
        status = 200
    }

    addBackup() { params, ResourceStreamHandle body ->
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
            message = 'A backup key is required'
            status = 400
            return
        }
        def cfg = ctx.centralConfig.mutableDescriptor
        def exc = 'excludedRepositories'
        if (json[exc] && json[exc] instanceof Iterable) {
            def bad = null
            def locals = cfg.localRepositoriesMap
            def remotes = cfg.remoteRepositoriesMap
            json[exc] = json[exc].collect {
                if (bad || !it) return null
                if (it in locals) return locals[it]
                if (it in remotes) return remotes[it]
                bad = it
                return null
            }
            if (bad) {
                message = "Excluded repository '$bad' does not exist"
                status = 409
                return
            }
            json[exc] = json[exc].findAll()
        }
        if (json['dir'] && json['dir'] instanceof String)
            json['dir'] = new File(json['dir'])
        def err = null
        def backup = new BackupDescriptor()
        propList.each { k, v ->
            if (!err && json[k] != null && !(v[0].isInstance(json[k]))) {
                err = "Property '$k' is type"
                err += " '${json[k].getClass().name}',"
                err += " should be a ${v[1]}"
            } else v[2](backup, json[k])
        }
        if (err) {
            message = err
            status = 400
            return
        }
        try {
            cfg.addBackup(backup)
        } catch (AlreadyExistsException ex) {
            message = "Backup with key '${json['key']}' already exists"
            status = 409
            return
        }
        ctx.centralConfig.descriptor = cfg
        status = 200
    }

    updateBackup() { params, ResourceStreamHandle body ->
        def key = params?.get('key')?.get(0) as String
        if (!key) {
            message = 'A backup key is required'
            status = 400
            return
        }
        def cfg = ctx.centralConfig.mutableDescriptor
        def backup = cfg.getBackup(key)
        if (backup == null) {
            message = "Backup with key '$key' does not exist"
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
                message = 'A backup key must not be empty'
                status = 400
                return
            } else if (json['key'] != key
                       && cfg.isBackupExists(json['key'])) {
                message = "Backup with key '${json['key']}' already exists"
                status = 409
                return
            }
        }
        def exc = 'excludedRepositories'
        if (json[exc] && json[exc] instanceof Iterable) {
            def bad = null
            def locals = cfg.localRepositoriesMap
            def remotes = cfg.remoteRepositoriesMap
            json[exc] = json[exc].collect {
                if (bad || !it) return null
                if (it in locals) return locals[it]
                if (it in remotes) return remotes[it]
                bad = it
                return null
            }
            if (bad) {
                message = "Excluded repository '$bad' does not exist"
                status = 409
                return
            }
            json[exc] = json[exc].findAll()
        }
        if (json['dir'] && json['dir'] instanceof String)
            json['dir'] = new File(json['dir'])
        def err = null
        propList.each { k, v ->
            if (!err && k in json.keySet()) {
                if (json[k] && !(v[0].isInstance(json[k]))) {
                    err = "Property '$k' is type"
                    err += " '${json[k].getClass().name}',"
                    err += " should be a ${v[1]}"
                } else v[2](backup, json[k])
            }
        }
        if (err) {
            message = err
            status = 400
            return
        }
        ctx.centralConfig.descriptor = cfg
        status = 200
    }
}
