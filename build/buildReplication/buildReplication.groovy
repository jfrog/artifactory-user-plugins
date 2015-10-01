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

import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import groovyx.net.http.ContentType
import groovyx.net.http.HTTPBuilder
import groovyx.net.http.HttpResponseException
@Grapes([
    @Grab(group = 'org.codehaus.groovy.modules.http-builder',
          module = 'http-builder', version = '0.6')
])
@GrabExclude('commons-codec:commons-codec')
import groovyx.net.http.Method
import org.apache.http.HttpRequestInterceptor
import org.apache.http.entity.StringEntity
import org.artifactory.exception.CancelException

import static groovyx.net.http.Method.PUT

/**
 * Build Replication plugin replicates All build info json from master
 * Artifactory to slave Artifactory instance.
 *
 * 1. Set Up:
 *   1.1. Place this script under the master Artifactory server
 *        ${ARTIFACTORY_HOME}/etc/plugins.
 *   1.2. Place buildReplication.properties file under
 *        ${ARTIFACTORY_HOME}/etc/plugins.
 * 2. Run the plugin:
 *  2.1. The plugin may run using the buildReplication.properties
 *  2.2. the file should contain:
 *       master=http://localhost:8080/artifactory
 *       masterUser=admin
 *       masterPassword=password
 *       slave=http://localhost:8081/artifactory
 *       slaveUser=admin
 *       slavePassword=password
 *       deleteDifferentBuild=true/false
 *
 *   2.3. Call the plugin execution by running:
 *   curl -X POST -v -u admin:password "http://localhost:8080/artifactory/api/plugins/execute/buildReplication"
 *
 *   2.4. You can pass all the parameters for the plugin in the REST call:
 *    * curl -X POST -v -u admin:password "http://localhost:8080/artifactory/api/plugins/execute/buildReplication?params=master=http://localhost:8080/artifactory|masterUser=admin|masterPassword=password|slave=http://localhost:8081/artifactory|slaveUser=admin|slavePassword=password|deleteDifferentBuild=false"
 *
 * 3. Logic:
 *   3.1. The plugin compare the list of build from master and slave and:
 *     3.1.1. Add all builds of projects that are found on master but not on
 *            slave.
 *     3.1.2. Projects that are found on slave but not on master are completely
 *            ignored.
 *   3.2. If deleteDifferentBuild=false:
 *     3.2.1. Builds deleted from master and already exists in slave will not be
 *            deleted.
 *   3.3. BE CAREFUL  deleteDifferentBuild=true BE CAREFUL
 *     3.3.1. Builds deleted from master and already exists in slave will be
 *            deleted from slave.
 *     If the delete artifacts flag is on inside the build info it will also
 *     delete artifacts on the slave
 *
 * The script can get 7 params:
 *  - Artifactory main server name (the replication will be from this server to
 *    the replicated one)
 *  - User name and password\security key to the main server
 *  - Artifactory replicated server
 *  - User name and password\security key to the replicated server
 *  - True\false flag which delete builds that don't exist at the main server
 *    and exist at the replicated server (not mandatory)
 *
 * curl -X POST -v -u admin:password "http://localhost:8080/artifactory/api/plugins/execute/buildReplication?params=master=masterArtifactory|masterUser=masterArtifactoryUser|masterPassword=masterPassword|slave=slaveArtifactory|slaveUser=slaveUser|slavePassword=slavePassword|deleteDifferentBuild=true/false"
 */

executions {
    buildReplication() { params ->
        try {
            // Defaults for success
            status = 200
            message = 'Builds were successfully replicated'

            binding.warnings = []
            binding.repProps = validate params

            String master = repProps.master
            if (!master.endsWith('/')) {
                master = master.concat('/')
            }
            String slave = repProps.slave
            if (!slave.endsWith('/')) {
                slave = slave.concat('/')
            }
            def masterMap = buildNameAndNumberes(master, repProps.masterUser, repProps.masterPassword)
            def slaveMap = buildNameAndNumberes(slave, repProps.slaveUser, repProps.slavePassword)
            def buildsDiff = masterMap - slaveMap

            if (buildsDiff.size() == 0 && (repProps.deleteDifferentBuild == false)) {
                log.info "Build are up to date, nothing to replicate"
            }

            buildsDiff.each {
                def buildColonLoc = it.lastIndexOf(':')
                def buildName = it[0..buildColonLoc - 1]
                String buildNumber = it.substring(buildColonLoc + 1)

                if (buildName == null || buildNumber == null) {
                    throw new CancelException("Error , There was problem with getting the buildName or buildNumber")
                }
                uploadJson(master, repProps.masterUser, repProps.masterPassword, slave, repProps.slaveUser, repProps.slavePassword, buildName, buildNumber)
            }

            if (repProps.deleteDifferentBuild == 'true') {
                def secondChanges = slaveMap - masterMap
                if (secondChanges.size() == 0) {
                    log.info "The builds info are up to date"
                }
                def masterBuildkeys = [] as Set
                masterMap.each { t ->
                    String buildName = org.apache.commons.lang.StringUtils.substringBeforeLast(t, ':/')
                    buildName = org.apache.commons.lang.StringUtils.removeStart(buildName, '/')
                    masterBuildkeys << buildName
                }

                def removeBuild = [:]

                secondChanges.each {
                    String buildNumber = org.apache.commons.lang.StringUtils.substringAfterLast(it, ':/')
                    String buildName = org.apache.commons.lang.StringUtils.substringBeforeLast(it, ':/')
                    buildName = org.apache.commons.lang.StringUtils.removeStart(buildName, '/')
                    // Don't delete project that only exist in slave
                    if (masterBuildkeys.contains(buildName)) {
                        if (removeBuild.containsKey(buildName)) {
                            String value = removeBuild.get(buildName) + ",$buildNumber"
                            removeBuild.put(buildName, value)
                        } else {
                            removeBuild.put(buildName, buildNumber)
                        }
                    }
                }

                removeBuild.each {
                    deleteBuild(it.key, it.value)
                }
            }
        } catch (CancelException e) {
            // aborts during execution
            status = e.status
            message = e.message
        }
    }
}

def validate(params) throws CancelException {
    Properties replicationProps
    File propertiesFile
    // If query parameters were sent then use them otherwise use properties file
    if (params.size() > 1) {
        replicationProps = new Properties()
        replicationProps.putAll(params)
        if (params.size() < 6) {
            handleError 400, "Not all required all parameters were sent, should be master, masterUser, masterPassword, slave, slaveUser, slavePassword, deleteDifferentBuild"
        }

        if (!replicationProps.master) {
            handleError 400, "Query params are missing property 'master'"
        }
        if (!replicationProps.masterUser) {
            handleError 400, "Query params are missing property 'masterUser'"
        }
        if (!replicationProps.masterPassword) {
            handleError 400, "Query params are missing property 'masterPassword'"
        }
        if (!replicationProps) {
            handleError 400, "Query params are missing property 'masterPassword'"
        }
        if (!replicationProps.slave) {
            handleError 400, "Query params are missing property 'slave'"
        }
        if (!replicationProps.slaveUser) {
            handleError 400, "Query params are missing property 'slaveUser'"
        }
        if (!replicationProps.slavePassword) {
            handleError 400, "Query params are missing property 'slavePassword'"
        }
        if (!replicationProps.deleteDifferentBuild) {
            handleError 400, "Query params are missing property 'deleteDifferentBuild'"
        }
    } else {
        propertiesFile = new File("$ctx.artifactoryHome.etcDir/plugins", "buildReplication.properties")
        if (!propertiesFile.isFile()) {
            handleError 400, "No profile properties file was found at ${propertiesFile.absolutePath}"
        }

        replicationProps = new Properties()
        replicationProps.load(new FileReader(propertiesFile))
    }

    if (!replicationProps) {
        handleError 400, "Failed to load properties file at ${propertiesFile.absolutePath}. Are the permissions right and is it properly formatted?"
    }

    if (!replicationProps.master) {
        handleError 400, "Master Server url is missing from profile properties file. Please add 'master' Artifactory url property to ${propertiesFile.absolutePath}"
    }
    if (!replicationProps.masterUser) {
        handleError 400, "Master Server username is missing from profile properties file. Please add 'masterUser' property to ${propertiesFile.absolutePath}"
    }
    if (!replicationProps.masterPassword) {
        handleError 400, "Master Server password is missing from profile properties file. Please add 'masterPassword' property to ${propertiesFile.absolutePath}"
    }
    if (!replicationProps) {
        handleError 400, "Failed to load properties file at ${propertiesFile.absolutePath}. Are the permissions right and is it properly formatted?"
    }
    if (!replicationProps.slave) {
        handleError 400, "Slave Server url is missing from profile properties file. Please add 'slave' Artifactory url property to ${propertiesFile.absolutePath}"
    }
    if (!replicationProps.slaveUser) {
        handleError 400, "Slave Server username is missing from profile properties file. Please add 'slaveUser' property to ${propertiesFile.absolutePath}"
    }
    if (!replicationProps.slavePassword) {
        handleError 400, "Slave Server password is missing from profile properties file. Please add 'slavePassword' property to ${propertiesFile.absolutePath}"
    }
    if (!replicationProps.deleteDifferentBuild) {
        handleError 400, "Delete Different Build is missing from profile properties file. Please add 'deleteDifferentBuild' property to ${propertiesFile.absolutePath}"
    }

    replicationProps
}

def buildNameAndNumberes(String server, String user, String password) {
    def http = new HTTPBuilder(server)
    http.client.addRequestInterceptor({ def httpRequest, def httpContext ->
        httpRequest.addHeader('Authorization', "Basic ${"${user}:${password}".getBytes().encodeBase64()}")
    } as HttpRequestInterceptor)
    http.contentType = ContentType.JSON

    def buildsNames = getBuildNames(server, user, password)
    def buildNamesAndNumbers = []

    buildsNames?.each { buildName ->
        buildName = URLDecoder.decode(buildName, "UTF-8")
        http.get(path: "api/build/$buildName") { resp, json ->
            json.buildsNumbers.uri.each { buildNumber ->
                buildNamesAndNumbers << buildName + ":" + buildNumber
            }
        }
    }
    return buildNamesAndNumbers
}

def getBuildNames(String server, String user, String password) {
    def http = new HTTPBuilder(server)
    def buildNames = []

    http.contentType = ContentType.JSON
    http.auth.basic(user, password)

    try {
        http.get(path: 'api/build') { resp, json ->
            json.builds.uri.each { name ->
                buildNames << name
            }
        }
    } catch (HttpResponseException e) {
        log.info("Could not get build information from : ${server}api/build")
    }
    return buildNames
}

def uploadJson(String mainServer, String mainServerUser, String mainServerPassword, String repServer, String repServerUser, String repServerPassword, String buildName, String buildNumber) {
    def http = new HTTPBuilder(mainServer)
    http.contentType = ContentType.JSON
    http.auth.basic(mainServerUser, mainServerPassword)
    http.parser.'application/json' = http.parser.'text/plain'
    http.encoder[ContentType.ANY] = { it }

    buildNumber = buildNumber.substring(1)
    buildName = buildName.substring(1)

    try {
        http.get(path: "api/build/$buildName/$buildNumber") { resp, json ->
            putCommand(repServer, repServerUser, repServerPassword, json.text)
        }
    } catch (HttpResponseException e) {
        log.info("Error get build information from : $mainServer/api/build/$buildName/$buildNumber")
    }
}

def putCommand(def server, def repServerUser, def repServerPassword, def json) {
    def http = new HTTPBuilder(server)
    def newJson = parseJson json

    http.client.addRequestInterceptor({ def httpRequest, def httpContext ->
        httpRequest.addHeader('Authorization', "Basic ${"${repServerUser}:${repServerPassword}".getBytes().encodeBase64()}")
    } as HttpRequestInterceptor)

    http.encoder[ContentType.JSON] = { it }
    http.request(PUT, ContentType.JSON) { req ->
        uri.path = "api/build"
        def entity = new StringEntity(newJson['build'], org.apache.http.entity.ContentType.APPLICATION_JSON)
        body = entity

        response.success = { resp, reader ->
            log.info "Successfully uploaded build ${newJson['name']}/${newJson['number']}"
        }

        response.failure = { resp ->
            if (resp.statusLine.statusCode == 404) {
                "404 Not Found, The file failed to upload"
            }
            if (resp.statusLine.statusCode == 401) {
                "401 Unauthorized, Username or password are wrong"
            } else {
                "Error $resp.statusLine.statusCode , the file failed to upload"
            }
        }
    }
}

def deleteBuild(String buildName, String buildNumbers) {
    def dbn = org.apache.commons.httpclient.util.URIUtil.encodeQuery(buildName)
    HTTPBuilder http = new HTTPBuilder(repProps.slave)
    http.client.addRequestInterceptor({ def httpRequest, def httpContext ->
        httpRequest.addHeader('Authorization', "Basic ${"${repProps.slaveUser}:${repProps.slavePassword}".getBytes().encodeBase64()}")
    } as HttpRequestInterceptor)

    http.request("$repProps.slave/api/build/$dbn", Method.DELETE, ContentType.ANY) { req ->
        uri.query = [buildNumbers: buildNumbers]
        headers.Accept = 'application/text'

        response.'200' = { resp, reader ->
            log.info("Deleted $buildName from $repProps.slave")
        }

        response.failure = { resp ->
            handleWarning(resp, "Error code : $resp.statusLine.statusCode")
        }
    }
}

def parseJson(def json) {
    def pJson = new JsonSlurper().parseText(json)
    pJson.remove('uri')
    def map = [name: pJson.buildInfo.get('name'), number: pJson.buildInfo.get('number')]
    JsonBuilder builder = new JsonBuilder(pJson)
    map << [build: org.apache.commons.lang.StringUtils.removeStart(builder.toString(), '{"buildInfo":')]
}

def handleError(int status, message) throws CancelException {
    log.error message
    throw new CancelException(message: message, status: status)
}

def handleError(resp, message) throws CancelException {
    message += ": ${resp.statusLine.reasonPhrase}"
    handleError(((int) resp.statusLine.statusCode), message)
}

def handleWarning(message) {
    log.warn message
    warnings << message
}

def handleWarning(resp, message) {
    message += ": ${resp.statusLine.reasonPhrase}"
    handleWarning message
}
