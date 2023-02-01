/*
 * Copyright (C) 2016 JFrog Ltd.
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

import org.artifactory.api.security.ldap.LdapService
import org.artifactory.security.InternalUsernamePasswordAuthenticationToken
import org.artifactory.security.ldap.InternalLdapAuthenticator

import org.springframework.security.core.AuthenticationException

realms {
    slashedADLogin(autoCreateUsers: true) {
        authenticate { username, credentials ->
            // Extract the domain and user, do initial checks
            def (domain, user) = getAuthInfo(username)
            if (!domain || !user) return false
            // Get the authenticator for this domain
            def authmanager = ctx.beanForType(InternalLdapAuthenticator)
            def authenticator = authmanager.authenticators[domain]
            if (!authenticator) return false
            // Create an authentication object from the username and password
            def authentication =
                new InternalUsernamePasswordAuthenticationToken(
                    user, credentials)
            // Run the authentication and return the result
            try {
                return authenticator.authenticate(authentication) != null
            } catch (AuthenticationException ex) {
                return false
            }
        }

        userExists { username ->
            // Extract the domain and user, do initial checks
            def (domain, user) = getAuthInfo(username)
            if (!domain || !user) return false
            // Get the LDAP setting for this domain
            def cfg = ctx.centralConfig.descriptor.security.ldapSettings
            def ldap = cfg?.find { it.key == domain }
            if (!ldap?.isEnabled()) return false
            // Get the LdapService to tell us if the user exists in the domain
            def ldapserv = ctx.beanForType(LdapService)
            return !ldapserv.getDnFromUserName(ldap, user)
        }
    }
}

// Return the domain and user extracted from the given username. If the domain
// does not exist, return null values instead.
def getAuthInfo(username) {
    // If LDAP is not enabled, don't authenticate
    if (!ctx.centralConfig.descriptor.security.isLdapEnabled()) return []
    def (domain, user) = username.split('\\+', 2)
    // If LDAP is not enabled, don't authenticate
    if (!user || username != "$domain+$user") return []
    // If the domain does not match an enabled LDAP config, don't authenticate
    def cfg = ctx.centralConfig.descriptor.security.ldapSettings
    def ldap = cfg?.find { it.key == domain }
    if (!ldap?.isEnabled()) return []
    // Otherwise, try to authenticate
    return [domain, user]
}
