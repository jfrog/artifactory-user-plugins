/*
 * Copyright (C) 2014 JFrog Ltd.
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

import org.artifactory.security.RealmPolicy
import org.artifactory.security.groups.LdapGroupsSettings

/**
 * An example of attaching Ldap groups to any user (can be internal, SSO etc..)
 *
 * @author Shay Yaakov
 */

realms {
    myrealm([autoCreateUsers: false, realmPolicy: RealmPolicy.SUFFICIENT]) {
        authenticate { username, credentials ->
            def settings = new LdapGroupsSettings()
            // 'il-users' is an existing Ldap Group Setting Name in Artifactory
            // All of it's groups will be fetched and attached to the user
            settings.ldapGroupSettingsName = 'il-users'
            groups += security.getCurrentUserGroupNames(settings)
            return true
        }
    }
}
