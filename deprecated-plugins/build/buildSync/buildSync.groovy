/*
 * Copyright (C) 2014-2021 JFrog Ltd.
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

import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import groovyx.net.http.HTTPBuilder

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
import org.artifactory.build.InternalBuildService
import org.artifactory.concurrent.ArtifactoryRunnable
import org.artifactory.exception.CancelException
import org.artifactory.storage.build.service.BuildStoreService
import org.artifactory.storage.db.DbService
import org.artifactory.util.HttpUtils
import org.artifactory.search.Searches
import org.artifactory.request.RequestThreadLocal

import java.util.List
import java.util.concurrent.Callable
import java.util.concurrent.Executors

import org.apache.commons.lang.StringUtils
import org.apache.http.HttpRequestInterceptor
import org.apache.http.StatusLine
import org.apache.http.impl.client.AbstractHttpClient
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.impl.conn.PoolingClientConnectionManager
import org.apache.http.params.HttpParams

import org.jfrog.build.api.Build
import org.slf4j.Logger
import org.springframework.security.core.context.SecurityContextHolder

import static groovyx.net.http.ContentType.BINARY
import static groovyx.net.http.ContentType.JSON
import static groovyx.net.http.ContentType.TEXT
import static groovyx.net.http.Method.DELETE
import static groovyx.net.http.Method.PUT
import static groovyx.net.http.Method.POST

/**
 * Refer to README.md for how to setup and install the Build Sync plugin.
 */
//
def baseConfHolder = new BaseConfigurationHolder(ctx, log)

executions {
    buildSyncPullConfig(groups: ['synchronizer']) { params ->
        try {
            def baseConf = baseConfHolder.getCurrent()
            if (baseConfHolder.errors) {
                status = 500
                message = "Configuration file is incorrect: ${baseConfHolder.errors.join("\n")}"
                return
            }
            String confKey = params?.get('key')?.get(0) as String
            if (!confKey || !baseConf.pullConfigs.containsKey(confKey)) {
                status = 400
                message = "buildSyncPullConfig needs a key params part of ${baseConf.pullConfigs.keySet()}"
                return
            }
            def maxS = params?.get('max')?.get(0) as String
            int max = maxS ? Integer.valueOf(maxS) : 0
            def forceS = params?.get('force')?.get(0) as String
            boolean force = forceS ? (forceS == "1" || forceS == "true") : false
            PullConfig pullConf = baseConf.pullConfigs[confKey]

            def res = doSync(
                new RemoteBuildService(pullConf.source, log, baseConf.ignoreStartDate),
                new LocalBuildService(ctx, log, pullConf.reinsert, pullConf.activatePlugins, baseConf.ignoreStartDate),
                pullConf.buildNames,
                pullConf.delete, max, force, baseConf.maxThreads, baseConf.ignoreStartDate,
                pullConf.syncPromotions
            )
            if (!res) {
                status = 404
                message = "Nothing found to synchronize using Pull ${pullConf.key}"
            } else {
                status = 200
                message = "Builds ${pullConf.buildNames} from ${pullConf.source.url}" +
                    " successfully replicated:\n${res.join('\n')}\n"
            }
        } catch (Exception e) {
            // aborts during execution
            log.error("Failed pull config", e)
            status = 500
            message = e.getMessage()
        }
    }

    buildSyncPushConfig(groups: ['synchronizer']) { params ->
        try {
            def baseConf = baseConfHolder.getCurrent()
            if (baseConfHolder.errors) {
                status = 500
                message = "Configuration file is incorrect: ${baseConfHolder.errors.join("\n")}"
                return
            }
            String confKey = params?.get('key')?.get(0) as String
            if (!confKey || !baseConf.pushConfigs.containsKey(confKey)) {
                status = 400
                message = "buildSyncPushConfig needs a key params part of ${baseConf.pushConfigs.keySet()}"
                return
            }
            def forceS = params?.get('force')?.get(0) as String
            boolean force = forceS ? (forceS == "1" || forceS == "true") : false
            PushConfig pushConf = baseConf.pushConfigs[confKey]

            List<String> res = []
            pushConf.destinations.each { destServer ->
                res.addAll(doSync(
                    new LocalBuildService(ctx, log, false, false, baseConf.ignoreStartDate),
                    new RemoteBuildService(destServer, log, baseConf.ignoreStartDate),
                    pushConf.buildNames,
                    pushConf.delete, 0, force, baseConf.maxThreads, baseConf.ignoreStartDate,
                    pushConf.syncPromotions
                ))
            }
            if (!res) {
                status = 404
                message = "Nothing found to synchronize using Push ${pushConf.key}"
            } else {
                status = 200
                message = "Builds ${pushConf.buildNames} were successfully replicated" +
                    " to ${pushConf.destinations.collect { it.url }}\n${res.join('\n')}\n"
            }
        } catch (Exception e) {
            // aborts during execution
            log.error("Failed push config", e)
            status = 500
            message = e.message
        }
    }
}

build {
    afterSave { buildRun ->
        try {
            def request = RequestThreadLocal.getRequest()?.getRequest()
            def propagate = request?.getParameter('propagate')
            if ('false' == propagate) {
                // Avoid sync loops by not firing event-base push for builds created by the plugin
                log.debug "Build request set to not propagate."
                return
            }
        } catch (MissingMethodException e) {
            log.warn "Artifactory 4.x or older does not support event push Build replication loop"
        }
        log.debug "Checking if ${buildRun.name} should be pushed!"
        def baseConf = baseConfHolder.getCurrent()
        if (!baseConfHolder.errors) {
            baseConf.eventPushConfigs.each { PushConfig pushConf ->
                pushConf.buildNames.each { String buildNameFilter ->
                    pushIfMatch(buildRun as DetailedBuildRunImpl, pushConf, buildNameFilter, baseConf.ignoreStartDate)
                }
            }
        }
    }
}

def pushIfMatch(DetailedBuildRunImpl buildRun, PushConfig pushConf, String buildNameFilter, ignoreStartDate) {
    String buildName = buildRun.name
    log.debug "Checking if ${buildRun.name} match $buildNameFilter"
    boolean match = false

    if (buildNameFilter.contains("*")) {
        try {
            String regex = buildNameFilter.replace("*",  ".*")
            match = buildName.matches(regex)
        } catch (Exception e) {
        }
    }

    if (!match) {
        match = buildName.equals(buildNameFilter);
    }

    if (match) {
        pushConf.destinations.each { Server server ->
            try {
                log.debug "Pushing ${buildRun.name}:${buildRun.number} to ${server.url}"
                def rbs = new RemoteBuildService(server, log, ignoreStartDate)
                rbs.addBuild(buildRun.build)
            } catch (Exception e) {
                log.error(
                    "Sending ${buildRun.name}:${buildRun.number} to ${server.url} failed with: ${e.getMessage()}",
                    e)
            }
        }
    }
}

def doSync(BuildListBase src, BuildListBase dest, List<String> buildNames, boolean delete,
    int max, boolean force, int maxThreads, boolean ignoreStartDate, boolean syncPromotions) {
    List res = []
    NavigableSet<BuildRun> buildNamesToSync = src.filterBuildNames(buildNames)
    def buildThreadPool = Executors.newFixedThreadPool(maxThreads)
    def set = max == 0 ? buildNamesToSync : buildNamesToSync.descendingSet()
    def authctx = SecurityContextHolder.context.authentication
    try {
        set.each { BuildRun srcBuild ->
            if (!force && !syncPromotions && srcBuild.started && srcBuild.started == dest.getLastStarted(srcBuild.name)) {
                log.debug "Build ${srcBuild} is already sync!"
                res << "${srcBuild.name}:already-synched"
            } else {
                String buildName = srcBuild.name
                def srcBuilds = src.getBuildNumbers(buildName, syncPromotions)
                def destBuilds = dest.getBuildNumbers(buildName, syncPromotions)
                def common = srcBuilds.intersect(destBuilds)
                log.info "Found ${common.size()} identical builds"
                def srcCommonBuilds = common.intersect(srcBuilds)
                def destCommonBuilds = common.intersect(destBuilds)
                destBuilds.removeAll(common)
                srcBuilds.removeAll(common)
                int added = 0
                int submitted = 0

                def futures = srcBuilds.collect { sb ->
                    if (max == 0 || submitted < max) {
                        submitted++
                        def task = [run:{
                            Build buildInfo = src.getBuildInfo(sb)
                            if (buildInfo) {
                                dest.addBuild(buildInfo)
                            }
                        }] as Runnable
                        buildThreadPool.submit(new ArtifactoryRunnable(task, ctx, authctx))
                    }
                }

                // Handle promotions change
                if (syncPromotions) {
                    common.each { b ->
                        def sb = srcCommonBuilds.find { it == b }
                        def db = destCommonBuilds.find { it == b }
                        if (sb.promotions != db.promotions) {
                            log.info "Found promotions change for build $sb"
                            if (max == 0 || submitted < max) {
                                submitted++
                                def task = [run:{
                                    Build buildInfo = src.getBuildInfo(sb)
                                    if (buildInfo) {
                                        dest.deleteBuild(db)
                                        dest.addBuild(buildInfo)
                                    }
                                }] as Runnable
                                futures << buildThreadPool.submit(new ArtifactoryRunnable(task, ctx, authctx))
                            }
                        }
                    }
                }

                futures.each { future ->
                    if (future) {
                        future.get()
                        added++
                    }
                }

                if (delete) {
                    destBuilds.each { BuildRun b ->
                        dest.deleteBuild(b)
                    }
                }
                res << "${srcBuild.name}:${common.size()}:$added:${srcBuilds.size()}:${destBuilds.size()}"
            }
        }
    } finally {
        buildThreadPool.shutdown()
    }
    res
}

abstract class BuildListBase {
    def log
    boolean ignoreStartDate = true
    private NavigableSet<BuildRun> _allLatestBuilds = null

    BuildListBase(log, ignoreStartDate) {
        this.log = log
        this.ignoreStartDate = ignoreStartDate as boolean
    }

    NavigableSet<BuildRun> getAllLatestBuilds() {
        if (_allLatestBuilds == null) {
            _allLatestBuilds = loadAllBuilds()
        }
        return _allLatestBuilds
    }

    abstract NavigableSet<BuildRun> loadAllBuilds()

    protected BuildRun createBuildRunFromJson(name, number, started, promotions = null) {
        TreeSet<Promotion> promotionStatus = new TreeSet<>()
        if (promotions != null) {
            promotions.each {
                promotionStatus << new Promotion(it['build.promotion.status'], it['build.promotion.created'] as String)
            }
        }
        new BuildRunImpl(name, number, started, ignoreStartDate, promotionStatus)
    }

    protected BuildRun createBuildRunFromDetailed(BuildRun br) {
        new BuildRunImpl(br.getName(), br.getNumber(), br.getStarted(), ignoreStartDate, new TreeSet<>())
    }

    NavigableSet<BuildRun> filterBuildNames(List<String> buildNames) {
        NavigableSet<BuildRun> result = new TreeSet<>()
        buildNames.each { String buildName ->
            if (buildName.contains('*')) {
                result.addAll(getAllLatestBuilds().findAll { it.name.matches(buildName) })
            } else {
                BuildRun found = getAllLatestBuilds().find { it.name == buildName }
                if (!found) {
                    found = createBuildRunFromJson(buildName, "", "")
                }
                result << found
            }
        }
        log.debug "After filter got ${result.size()} builds"
        result
    }

    String getLastStarted(String buildName) {
        getAllLatestBuilds().find { it.name == buildName }?.started
    }

    abstract NavigableSet<BuildRun> getBuildNumbers(String buildName, boolean includePromotions)

    abstract Build getBuildInfo(BuildRun b)

    abstract def addBuild(Build buildInfo)

    abstract def deleteBuild(BuildRun buildRun)
}

class ThreadSafeHTTPBuilder extends HTTPBuilder {
    protected AbstractHttpClient createClient(HttpParams params) {
        PoolingClientConnectionManager cm = new PoolingClientConnectionManager()
        cm.setMaxTotal(200) // Increase max total connection to 200
        cm.setDefaultMaxPerRoute(20) // Increase default max connection per route to 20
        new DefaultHttpClient(cm, params)
    }
}

class RemoteBuildService extends BuildListBase {
    Server server
    HTTPBuilder http
    StatusLine lastFailure = null
    String majorVersion, minorVersion

    RemoteBuildService(Server server, log, ignoreStartDate) {
        super(log, ignoreStartDate)
        this.server = server
        http = new ThreadSafeHTTPBuilder()
        http.uri = server.url
        // http.auth.basic(server.user, server.password)
        http.client.addRequestInterceptor({ def httpRequest, def httpContext ->
            httpRequest.addHeader('Authorization',
                "Basic ${"${server.user}:${server.password}".getBytes().encodeBase64()}")
        } as HttpRequestInterceptor)
        http.handler.failure = { resp ->
            lastFailure = resp.statusLine
        }
        log.info "Extracting Artifactory version from ${server.url}api/system/version"
        http.get(contentType: JSON, path: "artifactory/api/system/version") { resp, json ->
            log.info "Got Artifactory version ${json.version} and license ${json.license} from ${server.url}"
            def v = json.version.tokenize('.')
            if (v.size() < 3) {
                throw new CancelException("Server ${server.url} version not correct. Got ${json.text}",
                    500)
            }
            majorVersion = v[0]
            minorVersion = v[1]
        }
        if (lastFailure != null) {
            throw new CancelException("Server ${server.url} version unreadable! got: ${lastFailure.reasonPhrase}",
                lastFailure.statusCode)
        }
    }

    /**
     * Return a clear list of decoded build name from the remote server
     * @param server
     * @return
     */
    @Override
    NavigableSet<BuildRun> loadAllBuilds() {
        lastFailure = null
        Set<BuildRun> result = new TreeSet<>()
        log.info "Getting all builds from ${server.url}api/build"
        http.get(contentType: JSON, path: 'api/build') { resp, json ->
            json.builds.each { b ->
                result << createBuildRunFromJson(
                    HttpUtils.decodeUri(b.uri.substring(1)),
                    "LATEST",
                    b.lastStarted as String)
            }
        }
        if (lastFailure != null) {
            if (lastFailure.statusCode == 404) {
                log.info "No build present at ${server.url}"
            } else {
                def msg = "Could not get build information from ${server.url}api/build got: ${lastFailure.reasonPhrase}"
                log.error(msg)
                throw new CancelException(msg, lastFailure.statusCode)
            }
        }
        log.info "Found ${result.size()} builds in ${server.url}"
        result
    }

    @Override
    NavigableSet<BuildRun> getBuildNumbers(String buildName, boolean includePromotions) {
        lastFailure = null
        Set<BuildRun> result = new TreeSet<>()
        log.info "Getting all build numbers from ${server.url}api/build/$buildName"
        if (!includePromotions) {
            http.get(contentType: JSON, path: "api/build/$buildName") { resp, json ->
                json.buildsNumbers.each { b ->
                    result << createBuildRunFromJson(buildName,
                            HttpUtils.decodeUri(b.uri.substring(1)),
                            b.started as String)
                }
            }
        } else {
            http.request(POST) {
                uri.path = "api/search/aql"
                requestContentType = TEXT
                contentType = JSON
                body = "builds.find({\"name\":{\"\$eq\":\"$buildName\"}}).include(\"promotion\")"
                response.success = { resp, json ->

                    if (json.results && !json.results[0].containsKey('build.date') && !ignoreStartDate) {
                        def msg = "Server ${server.url} does not support promotions sync with ignoreStartDate set to false"
                        throw new CancelException(msg, 409)
                    }

                    json.results.each { b ->
                        result << createBuildRunFromJson(buildName,
                                b['build.number'],
                                b.containsKey('build.date') ? b['build.date'] as String : b['build.created'] as String,
                                b['build.promotions'])
                    }
                }
            }
        }
        if (lastFailure != null) {
            if (lastFailure.statusCode == 404) {
                log.info "No build ${buildName} exists at ${server.url}"
            } else {
                def msg = "Could not get build information from ${server.url}api/build/$buildName got: ${lastFailure.reasonPhrase}"
                log.error(msg)
                throw new CancelException(msg, lastFailure.statusCode)
            }
        }
        log.info "Found ${result.size()} remote builds named $buildName in ${server.url}"
        return result
    }

    @Override
    Build getBuildInfo(BuildRun b) {
        lastFailure = null
        def uri = "api/build/${b.name}/${b.number}"
        def queryParams = [:]
        if (!ignoreStartDate) {
            queryParams << [started: b.started]
        }
        log.info "Downloading JSON build info ${server.url}$uri ${queryParams}"
        Build res = null
        http.get(contentType: BINARY,
            headers: [Accept: 'application/json'],
            path: "$uri", query: queryParams) { resp, stream ->
            res = JacksonReader.streamAsClass(stream, BuildInfo.class).buildInfo
        }
        if (lastFailure != null) {
            if (lastFailure.statusCode == 404) {
                log.info "No build ${b} exists at ${server.url}"
            } else {
                def msg = "Could not get build info JSON from ${server.url}$uri got: ${lastFailure.reasonPhrase}"
                log.warn msg
            }
        }
        res
    }

    @Override
    def addBuild(Build buildInfo) {
        lastFailure = null
        http.request(PUT, JSON) {
            uri.path = "artifactory/api/build"
            // Avoid sync loops by not firing event-base push for builds created by the plugin
            uri.query = [propagate: 'false']
            body = JsonOutput.toJson(buildInfo)
            response.success = {
                log.info "Successfully uploaded build ${buildInfo.name}/${buildInfo.number} : ${buildInfo.started}"
            }
        }
        if (lastFailure != null) {
            def msg = "Could not insert build info ${buildInfo.name}/${buildInfo.number} : ${buildInfo.started} in ${server.url} got: ${lastFailure.reasonPhrase}"
            log.warn msg
        }
    }

    @Override
    def deleteBuild(BuildRun buildRun) {
        lastFailure = null
        http.request(DELETE, JSON) {
            uri.path = "api/build/${buildRun.name}"
            uri.query = [buildNumbers: buildRun.number, artifacts: 0, deleteAll: 0]
            if (!ignoreStartDate) {
                uri.query << [started: buildRun.started]
            }
            response.success = {
                log.info "Successfully deleted build ${buildRun.name}/${buildRun.number}"
            }
        }
        if (lastFailure != null) {
            def msg = "Could not delete build ${buildRun.name}/${buildRun.number} in ${server.url} got: ${lastFailure.reasonPhrase}"
            log.warn msg
        }
    }
}

class LocalBuildService extends BuildListBase {
    DbService dbService
    BuildStoreService buildStoreService
    InternalBuildService buildService
    AddonsManager addonsManager
    PluginsAddon pluginsAddon
    Builds builds
    Searches searches
    boolean reinsert
    boolean activatePlugins

    LocalBuildService(ctx, log, reinsert, activatePlugins, ignoreStartDate) {
        super(log, ignoreStartDate)
        dbService = ctx.beanForType(DbService.class)
        addonsManager = ctx.beanForType(AddonsManager.class)
        pluginsAddon = addonsManager.addonByType(PluginsAddon.class)
        buildStoreService = ctx.beanForType(BuildStoreService.class)
        buildService = ctx.beanForType(InternalBuildService.class)
        builds = ctx.beanForType(Builds.class)
        searches = ctx.beanForType(Searches.class)
        this.reinsert = reinsert
        this.activatePlugins = activatePlugins
    }

    @Override
    NavigableSet<BuildRun> loadAllBuilds() {
        def res = new TreeSet<BuildRun>()
        res.addAll(buildStoreService.getLatestBuildsByName().collect {
            createBuildRunFromJson(it.name, "LATEST", it.started)
        })
        log.info "Found ${res.size()} builds locally"
        res
    }

    @Override
    NavigableSet<BuildRun> getBuildNumbers(String buildName, boolean includePromotions) {
        def res = new TreeSet<BuildRun>()
        if (!includePromotions) {
            res.addAll(buildStoreService.findBuildsByName(buildName).collect { createBuildRunFromDetailed(it) })
        } else {
            def aqlQuery = "builds.find({\"name\":{\"\$eq\":\"$buildName\"}}).include(\"promotion\")"
            searches.aql(aqlQuery) { result ->
                def index = 0
                result.each { b ->
                    if (index == 0 && !b.containsKey('build.date') && !ignoreStartDate) {
                        def msg = "This server does not support promotions sync with ignoreStartDate set to false"
                        throw new CancelException(msg, 409)
                    }
                    index++
                    res << createBuildRunFromJson(buildName,
                            b['build.number'],
                            b.containsKey('build.date') ? b['build.date'] as String : b['build.created'] as String,
                            b['build.promotions'])
                }
            }
        }
        log.info "Found ${res.size()} local builds named $buildName"
        res
    }

    @Override
    def addBuild(Build buildInfo) {
        try {
            log.info "Deploying locally build ${buildInfo.name}:${buildInfo.number}:${buildInfo.started}"
            DetailedBuildRun detailedBuildRun = new DetailedBuildRunImpl(buildInfo)
            if (reinsert) {
                builds.saveBuild(detailedBuildRun)
            } else {
                dbService.invokeInTransaction("addBuild", new Callable<Object>() {
                    @Override
                    public Object call() throws Exception {
                        if (activatePlugins) {
                            pluginsAddon.execPluginActions(BeforeBuildSaveAction.class, builds, detailedBuildRun)
                        }
                        buildService.addBuild(buildInfo)
                        if (activatePlugins) {
                            pluginsAddon.execPluginActions(AfterBuildSaveAction.class, builds, detailedBuildRun)
                        }
                        return null
                    }
                })
            }
        } catch (Exception e) {
            String message = "Insertion of build  ${buildInfo.name}:${buildInfo.number}:${buildInfo.started} failed due to: ${e.getMessage()}"
            log.warn(message, e)
        }
    }

    @Override
    def deleteBuild(BuildRun buildRun) {
        if (ignoreStartDate) {
            def buildRuns = builds.getBuilds(buildRun.name, buildRun.number, null)
            buildRuns.each { builds.deleteBuild(it) }
        } else {
            try {
                log.info "Deleting local build $buildRun"
                builds.deleteBuild(buildRun)
            } catch (Exception e) {
                String message = "Deletion of build ${buildRun} failed due to: ${e.getMessage()}"
                log.warn(message, e)
            }
        }
    }

    @Override
    Build getBuildInfo(BuildRun b) {
        if (!ignoreStartDate) {
            try {
                return buildService.getBuild(b)
            } catch (MissingMethodException ex) {
                return buildStoreService.getBuildJson(b)
            }
        } else {
            def buildRuns = builds.getBuilds(b.name, b.number, null)
            try {
                return buildService.getBuild(buildRuns[0])
            } catch (MissingMethodException ex) {
                return buildStoreService.getBuildJson(buildRuns[0])
            }
        }
    }
}

class BaseConfigurationHolder {
    File confFile
    Logger log
    BaseConfiguration current = null
    long confFileLastChecked = 0L
    long confFileLastModified = 0L
    List<String> errors

    BaseConfigurationHolder(ctx, log) {
        this.log = log
        this.confFile = new File("${ctx.artifactoryHome.getEtcDir()}/plugins", "buildSync.json")
    }

    BaseConfiguration getCurrent() {
        log.debug "Retrieving current conf $confFileLastChecked $confFileLastModified $current"
        if (current == null || needReload()) {
            log.info "Reloading configuration from ${confFile.getAbsolutePath()}"
            if (!confFile || !confFile.exists()) {
                errors = ["The conf file ${confFile.getAbsolutePath()} does not exists!"]
            } else {
                try {
                    current = new BaseConfiguration(confFile, log)
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
        // Every 10secs check
        if ((System.currentTimeMillis() - confFileLastChecked) > 10000L) {
            !confFile.exists() || confFile.lastModified() != confFileLastModified
        } else {
            false
        }
    }
}

class BaseConfiguration {
    boolean ignoreStartDate = false
    int maxThreads = 10
    Map<String, Server> servers = [:]
    Map<String, PullConfig> pullConfigs = [:]
    Map<String, PushConfig> pushConfigs = [:]
    List<PushConfig> eventPushConfigs = []

    BaseConfiguration(File confFile, log) {
        def reader
        try {
            reader = new FileReader(confFile)
            def slurper = new JsonSlurper().parse(reader)
            ignoreStartDate = slurper.ignoreStartDate as boolean
            maxThreads = slurper.maxThreads as int
            log.info "BuildSync maxThreads=${maxThreads}"
            slurper.servers.each {
                def s = new Server(it)
                log.info "Adding Server ${s.key} : ${s.url}"
                servers.put(s.key, s)
            }
            slurper.pullConfigs.each {
                def p = new PullConfig(it, servers)
                log.info "Adding Pull Config ${p.key} reading from ${p.source?.key}"
                pullConfigs.put(p.key, p)
            }
            slurper.pushConfigs.each {
                def p = new PushConfig(it, servers)
                log.info "Adding Push Config ${p.key} pushing to [${p.destinations.collect { it?.key }.join(',')}]"
                pushConfigs.put(p.key, p)
                if (p.activateOnSave) {
                    eventPushConfigs.add(p)
                    log.info "Push Config ${p.key} activated for push on save"
                }
            }
        } finally {
            if (reader) {
                reader.close()
            }
        }
    }

    def findErrors() {
        if (!servers) {
            return ["No servers found or declared in build sync JSON configuration"]
        }
        List<String> errors = []
        BaseConfiguration conf = this
        errors.addAll(servers.values().collect { v -> v?.isInvalid() }.grep { it })
        errors.addAll(pullConfigs.values().collect { v -> v?.isInvalid(conf) }.grep { it })
        errors.addAll(pushConfigs.values().collect { v -> v?.isInvalid(conf) }.grep { it })
        errors
    }
}

class Server {
    String key, url, user, password

    Server(slurp) {
        key = slurp.key
        url = slurp.url
        if (url && !url.endsWith('/')) {
            url += '/'
        }
        user = slurp.user
        password = slurp.password
    }

    def isInvalid() {
        if (!key) {
            "Server configuration ${url} has no key!"
        } else if (!url) {
            "Server configuration ${key} has no URL!"
        } else if (!user || !password) {
            "Server configuration ${key} has no user or password!"
        } else {
            ""
        }
    }
}

class PullConfig {
    String key
    String sourceKey
    Server source
    List<String> buildNames
    boolean delete
    boolean reinsert
    boolean activatePlugins
    boolean syncPromotions

    PullConfig(def slurp, Map<String, Server> servers) {
        key = slurp.key
        sourceKey = slurp.source
        source = servers.get(sourceKey)
        buildNames = slurp.buildNames as List
        delete = slurp.delete as boolean
        reinsert = slurp.reinsert as boolean
        activatePlugins = slurp.activatePlugins as boolean
        syncPromotions = slurp.syncPromotions as boolean
    }

    def isInvalid(BaseConfiguration conf) {
        if (!key) {
            "Pull configuration ${source} has no key!"
        } else if (!sourceKey) {
            "Pull configuration ${key} has no source key defined!"
        } else if (!source) {
            "Pull configuration ${key} has source key ${sourceKey} which is not part of the servers ${conf.servers.keySet()}!"
        } else if (!buildNames || buildNames.any { !it }) {
            "Pull configuration ${key} has no build names or one empty build name in ${buildNames}!"
        } else {
            ""
        }
    }
}

class PushConfig {
    String key
    List<Server> destinations = []
    List<String> buildNames = []
    boolean delete
    boolean activateOnSave
    boolean syncPromotions

    PushConfig(def slurp, Map<String, Server> servers) {
        key = slurp.key
        slurp.destinations.each {
            def server = servers.get(it)
            if (server) {
                destinations << server
            }
        }
        buildNames = slurp.buildNames as List
        delete = slurp.delete as boolean
        activateOnSave = slurp.activateOnSave as boolean
        syncPromotions = slurp.syncPromotions as boolean
    }

    def isInvalid(BaseConfiguration conf) {
        if (!key) {
            "Push configuration ${destinations} has no key!"
        } else if (!destinations) {
            "Push configuration ${key} has no destinations found in the servers ${conf.servers.keySet()}!"
        } else if (!buildNames || buildNames.any { !it }) {
            "Push configuration ${key} has no build names or one empty build name in ${buildNames}!"
        } else {
            ""
        }
    }
}

class BuildRunImpl implements BuildRun, Comparable<BuildRun> {
    private final boolean ignoreStartDate;
    private final String name;
    private final String number;
    private final String started;
    private final SortedSet<Promotion> promotions;

    BuildRunImpl(String name, String number, String started, boolean ignoreStartDate, SortedSet<Promotion> promotions) {
        this.ignoreStartDate = ignoreStartDate;
        this.name = name;
        this.number = number;
        if (StringUtils.isNotBlank(started)) {
            this.started = BuildInfoUtils.formatBuildTime(BuildInfoUtils.parseBuildTime(started));
        } else {
            this.started = "";
        }
        this.promotions = promotions;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getNumber() {
        return number;
    }

    @Override
    public String getStarted() {
        return started;
    }

    @Override
    public Date getStartedDate() {
        return new Date(BuildInfoUtils.parseBuildTime(started));
    }

    @Override
    public String getCiUrl() {
        return null;
    }

    @Override
    public String getReleaseStatus() {
        return null;
    }

    public SortedSet<Promotion> getPromotions() {
        return promotions;
    }

    @Override
    int compareTo(BuildRun o) {
        int i
        if (!ignoreStartDate) {
            i = getStartedDate().compareTo(o.getStartedDate())
            if (i != 0) {
                return i
            }
        }
        i = name.compareTo(o.getName())
        if (i != 0) {
            return i
        }
        return number.compareTo(o.getNumber())
    }

    @Override
    public boolean equals(Object o) {
        BuildRun buildRun = (BuildRun) o;

        if (!ignoreStartDate && !started.equals(buildRun.started)) {
            return false;
        }
        if (!name.equals(buildRun.name)) {
            return false;
        }
        if (!number.equals(buildRun.number)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + number.hashCode();
        if (!ignoreStartDate)
            result = 31 * result + started.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "" + name + ':' + number + ':' + started + ':' + promotions;
    }
}

class Promotion implements Comparable<Promotion>{

    private final String status;
    private final String created;

    public Promotion(String status, String created) {
        this.status = status;
        this.created = created;
    }

    public String getStatus() {
        return status;
    }

    public String getCreated() {
        return created;
    }

    @Override
    public int compareTo(Promotion o) {
        int i = getCreated().compareTo(o.getCreated());
        if (i != 0) {
            return i;
        }
        return getStatus().compareTo(o.getStatus());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) { return true; }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Promotion promotion = (Promotion) o;
        if (status != null ? !status.equals(promotion.status) : promotion.status != null) {
            return false;
        }
        return created != null ? created.equals(promotion.created) : promotion.created == null;
    }

    @Override
    public int hashCode() {
        int result = status != null ? status.hashCode() : 0;
        result = 31 * result + (created != null ? created.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "" + status + ":" + created;
    }
}
