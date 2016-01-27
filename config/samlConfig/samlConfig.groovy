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
import org.artifactory.descriptor.security.sso.SamlSettings
import org.artifactory.resource.ResourceStreamHandle

def propList = ['enableIntegration': [
        Boolean.class, 'boolean',
        { c, v -> c.enableIntegration = v ?: false }
    ], 'loginUrl': [
        CharSequence.class, 'string',
        { c, v -> c.loginUrl = v ?: null }
    ], 'logoutUrl': [
        CharSequence.class, 'string',
        { c, v -> c.logoutUrl = v ?: null }
    ], 'serviceProviderName': [
        CharSequence.class, 'string',
        { c, v -> c.serviceProviderName = v ?: null }
    ], 'noAutoUserCreation': [
        Boolean.class, 'boolean',
        { c, v -> c.noAutoUserCreation = v ?: false }
    ], 'certificate': [
        CharSequence.class, 'string',
        { c, v -> c.certificate = v ?: null }]]

executions {
    getSaml(version: '1.0', httpMethod: 'GET') { params ->
        def cfg = ctx.centralConfig.descriptor.security.samlSettings
        if (cfg == null) cfg = new SamlSettings()
        def json = [
            enableIntegration: cfg.isEnableIntegration() ?: false,
            loginUrl: cfg.loginUrl ?: null,
            logoutUrl: cfg.logoutUrl ?: null,
            serviceProviderName: cfg.serviceProviderName ?: null,
            noAutoUserCreation: cfg.noAutoUserCreation ?: false,
            certificate: cfg.certificate ?: null]
        message = new JsonBuilder(json).toPrettyString()
        status = 200
    }

    setSaml(version: '1.0') { params, ResourceStreamHandle body ->
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
        if ('serviceProviderName' in json.keySet() &&
            !json['serviceProviderName']) {
            message = "Property 'serviceProviderName' must not be empty"
            status = 400
            return
        }
        if ('loginUrl' in json.keySet() &&
            !(json['loginUrl'] ==~ '^(ftp|https?)://.+$')) {
            message = "Property 'loginUrl' must be a valid URL"
            status = 400
            return
        }
        if ('logoutUrl' in json.keySet() &&
            !(json['logoutUrl'] ==~ '^(ftp|https?)://.+$')) {
            message = "Property 'logoutUrl' must be a valid URL"
            status = 400
            return
        }
        def cfg = ctx.centralConfig.mutableDescriptor
        def saml = cfg.security.samlSettings
        if (saml == null) saml = new SamlSettings()
        def err = null
        propList.each { k, v ->
            if (!err && k in json.keySet()) {
                if (json[k] && !(v[0].isInstance(json[k]))) {
                    err = "Property '$k' is type"
                    err += " '${json[k].getClass().name}',"
                    err += " should be a ${v[1]}"
                } else v[2](saml, json[k])
            }
        }
        if (err) {
            message = err
            status = 400
            return
        }
        cfg.security.samlSettings = saml
        ctx.centralConfig.saveEditedDescriptorAndReload(cfg)
        status = 200
    }
}
