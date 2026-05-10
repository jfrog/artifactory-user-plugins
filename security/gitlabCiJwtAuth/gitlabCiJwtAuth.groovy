/*
 * Copyright (C) 2021 MX Technologies, Inc.
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
@Grab(group='com.auth0', module='java-jwt', version='3.18.1')
@Grab(group='com.auth0', module='jwks-rsa', version='0.19.0')
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import com.auth0.jwk.UrlJwkProvider

import org.artifactory.api.security.UserGroupService

import java.security.interfaces.RSAPublicKey

/**
 *
 * This plugin allows gitlab projects to authenticate directly with artifactory
 * using job-specific signed JWT tokens and the claims they contain to drive
 * dynamic authorization policies without the need to manage users and credentials
 * between gitlab and artifactory.
 *
 * @author <a href="mailto:josh@6bit.com">Josh Perry</a>
 * @since 08/24/2021
 */

@Field final String ISSUER = 'gitlab.example.com'
@Field final String IGNORE_ROOT = 'org/'

realms {

  gitlabJwtRealm(autoCreateUsers: false) {

    authenticate { username, credentials ->
      try {
        // Only handle requests with the virtual user `gitlabci`
        if (username != 'gitlabci') return false

        // Check if this is a gitlab JWT credential
        if (!credentials.startsWith('{JWT}')) return false
        def realjwt = credentials.drop(5)

        log.info('got gitlabci JWT auth request')

        // Pre-decode the (currently) untrusted token
        def prejwt = JWT.decode(realjwt)

        log.debug('loading jwks provider')
        // Setup the jwks validation with the key ID from the token
        def provider = new UrlJwkProvider(new URL("https://$ISSUER/-/jwks"))
        def jwk = provider.get(prejwt.getKeyId())
        def algo = Algorithm.RSA256((RSAPublicKey)jwk.getPublicKey(), null)

        log.debug('creating verifier')
        // Create the verification policy
        def verifier = JWT.require(algo)
                          .withIssuer(ISSUER)
                          .build()

        log.debug('verifying token')
        // Verify and get a trusted decoded token
        def jwt = verifier.verify(realjwt)

        // Get the project path without a particular root
        def path = jwt.getClaim('project_path').asString().toLowerCase()
        if (path.startsWith(IGNORE_ROOT))
          path = path.drop(IGNORE_ROOT.length())
        log.debug('got project path claim {}', path)

        // Find and attach any `gitlab-` groups for each level of the project path
        def paths = path.split('/')
        def groupsvc = ctx.beanForType(UserGroupService.class)
        asSystem {
          for(x in (0..paths.size()-1)) {
            def groupname = "gitlab-${paths[0..x].join('-')}"

            log.debug('checking for group {}', groupname)
            if(groupsvc.findGroup(groupname) != null) {
              log.debug('attaching group {}', groupname)
              groups += groupname
            }
          }
        }

        return true
      } catch(JWTVerificationException e) {
        log.error('Error verifying jwt signature')
      } catch(Exception e) {
        log.error("Unexpected Error: ${e}")
      }

      return false
    }

    userExists { username ->
      return username == 'gitlabci'
    }

  }

}
