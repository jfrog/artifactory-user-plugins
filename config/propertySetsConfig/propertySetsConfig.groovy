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
import org.artifactory.descriptor.property.*
import org.artifactory.resource.ResourceStreamHandle
import org.artifactory.util.AlreadyExistsException

def propList = ['name': [
        CharSequence.class, 'string',
        { c, v -> c.name = v ?: null }
    ], 'visible': [
        Boolean.class, 'boolean',
        { c, v -> c.visible = v ?: false }
    ], 'properties': [
        Iterable.class, 'list',
        { c, v -> c.properties = validateProps(v) ?: [] }]]

def setPropType(c, propType) {
    switch (propType) {
        case "MULTI_SELECT":
            c.closedPredefinedValues = true
            c.multipleChoice = true
            break
        case "SINGLE_SELECT":
            c.closedPredefinedValues = true
            c.multipleChoice = false
            break
        default:
            c.closedPredefinedValues = false
            c.multipleChoice = false
    }
}

def validateDefaults(dflts) {
    def propDefaultList = ['value': [
            CharSequence.class, 'string',
            { c, v -> c.value = v ?: null }
        ], 'defaultValue': [
            Boolean.class, 'boolean',
            { c, v -> c.defaultValue = v ?: false }]]
    def dlist = []
    for (dflt in dflts) {
        if (!(dflt instanceof Map)) {
            def err = 'Provided property default must be a JSON object,'
            err += " is instead '${new JsonBuilder(dflt).toString()}'"
            throw new RuntimeException(err)
        }
        def dobj = new PredefinedValue()
        propDefaultList.each { k, v ->
            if (dflt[k] != null && !(v[0].isInstance(dflt[k]))) {
                def err = "Property '$k' is type"
                err += " '${dflt[k].getClass().name}',"
                err += " should be a ${v[1]}"
                throw new RuntimeException(err)
            } else v[2](dobj, dflt[k])
        }
        dlist << dobj
    }
    return dlist
}

def validateProps(props) {
    def propValList = ['name': [
            CharSequence.class, 'string',
            { c, v -> c.name = v ?: null }
        ], 'predefinedValues': [
            Iterable.class, 'list',
            { c, v -> c.predefinedValues = validateDefaults(v) ?: [] }
        ], 'propertyType': [
            CharSequence.class, 'string',
            { c, v -> setPropType(c, v) }]]
    def plist = []
    for (prop in props) {
        if (!(prop instanceof Map)) {
            def err = 'Provided property must be a JSON object,'
            err += " is instead '${new JsonBuilder(prop).toString()}'"
            throw new RuntimeException(err)
        }
        if (!prop['name']) {
            def err = 'A property name is required for property'
            err += " '${new JsonBuilder(prop).toString()}'"
            throw new RuntimeException(err)
        }
        def pobj = new Property()
        propValList.each { k, v ->
            if (prop[k] != null && !(v[0].isInstance(prop[k]))) {
                def err = "Property '$k' is type"
                err += " '${prop[k].getClass().name}',"
                err += " should be a ${v[1]}"
                throw new RuntimeException(err)
            } else v[2](pobj, prop[k])
        }
        plist << pobj
    }
    return plist
}

executions {
    getPropertySetsList(httpMethod: 'GET') { params ->
        def cfg = ctx.centralConfig.descriptor.propertySets
        if (cfg == null) cfg = []
        def json = cfg.collect { it.name }
        message = new JsonBuilder(json).toPrettyString()
        status = 200
    }

    getPropertySet(httpMethod: 'GET') { params ->
        def name = params?.get('name')?.get(0) as String
        if (!name) {
            message = 'A property set name is required'
            status = 400
            return
        }
        def propertySet = ctx.centralConfig.descriptor.propertySets.find {
            it.name == name
        }
        if (propertySet == null) {
            message = "Property set with name '$name' does not exist"
            status = 404
            return
        }
        def props = propertySet.properties.collect {
            def vals = it.predefinedValues.collect {
                return [
                    value: it.value ?: null,
                    defaultValue: it.isDefaultValue() ?: false]
            }
            return [
                name: it.name ?: null, predefinedValues: vals,
                propertyType: it.propertyType ?: 'ANY_VALUE']
        }
        def json = [
            name: propertySet.name ?: null,
            visible: propertySet.isVisible() ?: false,
            properties: props]
        message = new JsonBuilder(json).toPrettyString()
        status = 200
    }

    deletePropertySet(httpMethod: 'DELETE') { params ->
        def name = params?.get('name')?.get(0) as String
        if (!name) {
            message = 'A property set name is required'
            status = 400
            return
        }
        def cfg = ctx.centralConfig.mutableDescriptor
        def propertySet = cfg.removePropertySet(name)
        if (propertySet == null) {
            message = "Property set with name '$name' does not exist"
            status = 404
            return
        }
        ctx.centralConfig.descriptor = cfg
        status = 200
    }

    addPropertySet() { params, ResourceStreamHandle body ->
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
            message = 'A property set name is required'
            status = 400
            return
        }
        def err = null
        def propertySet = new PropertySet()
        propList.each { k, v ->
            if (!err && json[k] != null && !(v[0].isInstance(json[k]))) {
                err = "Property '$k' is type"
                err += " '${json[k].getClass().name}',"
                err += " should be a ${v[1]}"
            } else {
                try {
                    v[2](propertySet, json[k])
                } catch (RuntimeException ex) {
                    err = ex.message
                }
            }
        }
        if (err) {
            message = err
            status = 400
            return
        }
        def cfg = ctx.centralConfig.mutableDescriptor
        try {
            cfg.addPropertySet(propertySet)
        } catch (AlreadyExistsException ex) {
            message = "Property set with name '${json['name']}' already exists"
            status = 409
            return
        }
        ctx.centralConfig.descriptor = cfg
        status = 200
    }

    updatePropertySet() { params, ResourceStreamHandle body ->
        def name = params?.get('name')?.get(0) as String
        if (!name) {
            message = 'A property set name is required'
            status = 400
            return
        }
        def cfg = ctx.centralConfig.mutableDescriptor
        def propertySet = cfg.propertySets.find {
            it.name == name
        }
        if (propertySet == null) {
            message = "Property set with name '$name' does not exist"
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
                message = 'A property set name must not be empty'
                status = 400
                return
            } else if (json['name'] != name
                       && cfg.isPropertySetExists(json['name'])) {
                message = "Property set with name '${json['name']}' already exists"
                status = 409
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
                } else {
                    try {
                        v[2](propertySet, json[k])
                    } catch (RuntimeException ex) {
                        err = ex.message
                    }
                }
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
