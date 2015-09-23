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

executions {
    getSaml(httpMethod: 'GET') { params ->
        def cfg = ctx.centralConfig.descriptor.security.samlSettings
        if (cfg == null) cfg = new SamlSettings()
        def json = [
            enableIntegration: cfg.isEnableIntegration(),
            loginUrl: cfg.loginUrl, logoutUrl: cfg.logoutUrl,
            serviceProviderName: cfg.serviceProviderName,
            noAutoUserCreation: cfg.noAutoUserCreation,
            certificate: cfg.certificate]
        message = new JsonBuilder(json).toPrettyString()
        status = 200
    }

    setSaml() { params, ResourceStreamHandle body ->
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
        def cfg = ctx.centralConfig.mutableDescriptor
        def saml = cfg.security.samlSettings
        if (saml == null) saml = new SamlSettings()
        def err = null
        if (!err && 'enableIntegration' in json.keySet()) {
            if (!(json['enableIntegration'] instanceof Boolean)) {
                err = "Property 'enableIntegration' is type"
                err += " '${json['enableIntegration'].getClass()}',"
                err += " should be type 'boolean'"
            } else saml.enableIntegration = json['enableIntegration']
        }
        if (!err && 'loginUrl' in json.keySet()) {
            if (!json['loginUrl']) json['loginUrl'] = null
            if (json['loginUrl']
                && !(json['loginUrl'] instanceof CharSequence)) {
                err = "Property 'loginUrl' is type"
                err += " '${json['loginUrl'].getClass()}',"
                err += " should be type 'string'"
            } else saml.loginUrl = json['loginUrl']
        }
        if (!err && 'logoutUrl' in json.keySet()) {
            if (!json['logoutUrl']) json['logoutUrl'] = null
            if (json['logoutUrl']
                && !(json['logoutUrl'] instanceof CharSequence)) {
                err = "Property 'logoutUrl' is type"
                err += " '${json['logoutUrl'].getClass()}',"
                err += " should be type 'string'"
            } else saml.logoutUrl = json['logoutUrl']
        }
        if (!err && 'serviceProviderName' in json.keySet()) {
            if (!json['serviceProviderName']) json['serviceProviderName'] = null
            if (json['serviceProviderName']
                && !(json['serviceProviderName'] instanceof CharSequence)) {
                err = "Property 'serviceProviderName' is type"
                err += " '${json['serviceProviderName'].getClass()}',"
                err += " should be type 'string'"
            } else saml.serviceProviderName = json['serviceProviderName']
        }
        if (!err && 'noAutoUserCreation' in json.keySet()) {
            if (!(json['noAutoUserCreation'] instanceof Boolean)) {
                err = "Property 'noAutoUserCreation' is type"
                err += " '${json['noAutoUserCreation'].getClass()}',"
                err += " should be type 'boolean'"
            } else saml.noAutoUserCreation = json['noAutoUserCreation']
        }
        if (!err && 'certificate' in json.keySet()) {
            if (!json['certificate']) json['certificate'] = null
            if (json['certificate']
                && !(json['certificate'] instanceof CharSequence)) {
                err = "Property 'certificate' is type"
                err += " '${json['certificate'].getClass()}',"
                err += " should be type 'string'"
            } else saml.certificate = json['certificate']
        }
        if (err) {
            message = err
            status = 400
            return
        }
        cfg.security.samlSettings = saml
        ctx.centralConfig.descriptor = cfg
        status = 200
    }
}
