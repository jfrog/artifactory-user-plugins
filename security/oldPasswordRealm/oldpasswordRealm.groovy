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


@Grapes([
        @Grab(group = 'org.codehaus.groovy.modules.http-builder',
                module = 'http-builder', version = '0.7.2')
])
@GrabExclude('commons-codec:commons-codec')

import groovy.transform.Field
import groovyx.net.http.ContentType
import groovyx.net.http.HTTPBuilder
import groovyx.net.http.Method
import groovy.json.JsonSlurper
import org.artifactory.security.User
import groovyx.net.http.HTTPBuilder
import org.apache.commons.lang.StringUtils
import org.apache.http.HttpRequestInterceptor
import org.apache.http.StatusLine
import org.artifactory.addon.AddonsManager
import org.artifactory.addon.plugin.PluginsAddon
import org.artifactory.addon.plugin.build.AfterBuildSaveAction
import org.artifactory.addon.plugin.build.BeforeBuildSaveAction
import org.artifactory.api.jackson.JacksonReader
import org.artifactory.api.rest.build.BuildInfo
import org.artifactory.build.BuildInfoUtils
import org.artifactory.build.BuildRun
import org.artifactory.build.Builds
import org.artifactory.build.DetailedBuildRun
import org.artifactory.build.DetailedBuildRunImpl
import org.artifactory.exception.CancelException
import org.artifactory.storage.build.service.BuildStoreService
import org.artifactory.storage.db.DbService
import org.artifactory.util.HttpUtils
import org.jfrog.build.api.Build
import org.slf4j.Logger
import org.artifactory.security.User
import org.slf4j.Logger
import groovy.json.JsonSlurper
/**
 *
 * @author Naren Yadav
 * @since 08/24/16
 */



realms {
    oldpasswordRealm(autoCreateUsers: false) {
        authenticate { username, credentials ->
            log.debug "Authenticating '${username}'"
//           log.info "credentials '${credentials}'"
            def userConfHolder = new UserPwdConfigurationHolder(ctx, log)
            try {

                def userConf = userConfHolder.getCurrent()
                if (userConfHolder.errors) {
                    status = 500
                    message = "Configuration file is incorrect: ${userConfHolder.errors.join("\n")}"
                    return false
                }

                boolean passed = false
                if (username && userConf.users.contains("$username:$credentials")) {
                    asSystem {
                        if (security.findUser(username) == null) {
                            log.error "The user configuration conatin a username that does not exists in the DB"
                            passed = false
                        } else {
                            log.info "Using old password for '${username}'"
                            passed = true
                        }
                    }
                    return passed
                }
                return false
            }
            catch (Throwable th) {
                log.error(th.getMessage(), th)
            }
        }
    }
}

class UserPwdConfigurationHolder {
    File confFile
    Logger log
    UserPwdConfiguration current = null
    long confFileLastChecked = 0L
    long confFileLastModified = 0L
    List<String> errors

    UserPwdConfigurationHolder(ctx, log) {
        this.log = log
        this.confFile = new File("${ctx.artifactoryHome.getHaAwareEtcDir()}/plugins", "users.json")
    }

    UserPwdConfiguration getCurrent() {
        log.debug "Retrieving current conf $confFileLastChecked $confFileLastModified $current"
        if (current == null || needReload()) {
            log.debug "Reloading configuration from ${confFile.getAbsolutePath()}"
            if (!confFile || !confFile.exists()) {
                errors = ["The conf file ${confFile.getAbsolutePath()} does not exists!"]
            } else {
                try {
                    current = new UserPwdConfiguration(confFile, log)
                    errors = current.findErrors()
                    if (errors.isEmpty()) {
                        confFileLastChecked = System.currentTimeMillis()
                        confFileLastModified = confFile.lastModified()
                    }
                } catch (Exception e) {
                    def msg = "Something not good happen during parsing: ${e.getMessage()}"
                    log.error(msg, e)
                    errors = [msg]
                }
            }
            if (errors) {
                log.error(
                        "Some validation errors appeared while parsing ${confFile.absolutePath}\n" +
                                "${errors.join("\n")}")
            }
        }
        current
    }

    boolean needReload() {
        // Every 120secs check
        if ((System.currentTimeMillis() - confFileLastChecked) > 1200000L) {
            !confFile.exists() || confFile.lastModified() != confFileLastModified
        } else {
            false
        }
    }
}

class UserPwdConfiguration {
    Set<String> users = [] as Set

    UserPwdConfiguration(File confFile, log) {
        def reader
        try {
            reader = new FileReader(confFile)
            def slurper = new JsonSlurper().parse(reader)
            slurper.users.each {
                log.debug "Adding harcoded password for ${it.user}"
                users.add("${it.user}:${it.password}")
            }
        }
        finally {
            if (reader) {
                reader.close()
            }
        }
    }


    def findErrors() {
        if (!users) {
            return ["No users found or declared in build sync JSON configuration"]
        }
        return []
    }
}
