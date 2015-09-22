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
        def json = new JsonSlurper().parse(reader)
        def cfg = ctx.centralConfig.mutableDescriptor
        def saml = cfg.security.samlSettings
        if (saml == null) saml = new SamlSettings()
        if ('enableIntegration' in json.keySet())
            saml.enableIntegration = json['enableIntegration']
        if ('loginUrl' in json.keySet())
            saml.loginUrl = json['loginUrl']
        if ('logoutUrl' in json.keySet())
            saml.logoutUrl = json['logoutUrl']
        if ('serviceProviderName' in json.keySet())
            saml.serviceProviderName = json['serviceProviderName']
        if ('noAutoUserCreation' in json.keySet())
            saml.noAutoUserCreation = json['noAutoUserCreation']
        if ('certificate' in json.keySet())
            saml.certificate = json['certificate']
        cfg.security.samlSettings = saml
        ctx.centralConfig.descriptor = cfg
        status = 200
    }
}
