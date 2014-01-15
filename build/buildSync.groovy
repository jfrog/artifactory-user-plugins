/*
 * Build Synchronization plugin replicates some build info json from one Artifactory
 * to multiple remotes Artifactory instances.
 * In this document "current server" means the Artifactory server where the plugin
 * is installed and running.
 * And "remote server" or "remote servers" defines the Artifactory servers accessed
 * via URL from this plugin. The URLs are configured either as fields in JSON to the REST
 * query or as part of the buildSync.json file.
 *
 * This plugin can work in 3 modes:
 * 1. Pull: Reading build info from a remote server source and adding them
 *          to the current Artifactory server.
 * 2. Push: Deploying a list of build info from the current server and adding them
 *          to the remote servers.
 * 3. Event Push: Adding all (or certain) build info as they are deployed
 *          on the current server to the remote servers.
 *
 * 1. Set Up:
 *   1.1. Edit the ${ARTIFACTORY_HOME}/etc/logback.xml to add:
 *       <logger name="buildSync">
 *           <level value="debug"/>
 *       </logger>
 *   1.2. Edit the buildSync.json file:
 *     1.2.1 First list the servers with:
 *       "servers": [
 *         {
 *           "key": "local-1",
 *           "url": "http://localhost:8080/artifactory",
 *           "user": "admin",
 *           "password": "password"
 *         },
 *         ...
 *       ]
 *       Each server should have a unique key identifying it, and url/user/pass used to access the REST API.
 *     1.2.2 Then list the pull replication configurations with:
 *       "pullConfigs": [
 *         {
 *            "key": "AllFrom2",
 *            "source": "local-2",
 *            "buildNames": [".*"],
 *            "delete": true,
 *            "reinsert": false,
 *            "activatePlugins": true
 *         },
 *         ...
 *       ]
 *       Each pull configuration should have a unique key identifying it. (mandatory)
 *       The source is pointing to one server key and should exists in the above list of servers. (mandatory)
 *       The buildNames are a list of string to filter build names to synchronized. (mandatory)
 *        If the string contains a '*' star character it is considered as a regular expression.
 *        If not the build name should be exactly equal (with case sensitivity).
 *       The delete flag tells the synchronization to delete all the local build numbers
 *        that do not exists in the remote server. (Optional, false by default)
 *       The reinsert flag tells the synchronization to fully reinsert the build info locally. (Optional, false by default)
 *        This will activate all the plugins associated with build deployment:
 *        - Change Artifactory deployer to current user,
 *        - Activate Issues aggregation,
 *        - Activate Third Party Control, or OSS Governance,
 *        - Activate all Users Plugins
 *       The activatePlugins flag will add the new build info as is and activate only the User Plugins. (Optional, false by default)
 *     1.2.3 Then list the push replication configurations with:
 *       "pushConfigs": [
 *         {
 *            "key": "PushTo23",
 *            "destinations": [ "local-2", "local-3" ],
 *            "buildNames": [".*"],
 *            "delete": true,
 *            "activateOnSave": false
 *         },
 *         ...
 *       ]
 *       Each push configuration should have a unique key identifying it. (mandatory)
 *       The destinations is pointing to a list of server key and should exists in the above list of servers. (mandatory)
 *       The buildNames are a list of string to filter build names to synchronized. (mandatory)
 *        If the string contains a '*' star character it is considered as a regular expression.
 *        If not the build name should be exactly equal (with case sensitivity).
 *       The delete flag tells the synchronization to delete all the remote build numbers
 *        that do not exists in the local server. (Optional, false by default)
 *       The onSave flag will add a listener in this plugin that will trigger push as soon as a new build arrives. (Optional, false by default)
 *       IMPORTANT: In Push mode a full reinsert is done on the remote server.
 *   1.3. Place buildSync.json file under ${ARTIFACTORY_HOME}/etc/plugins.
 *   1.4. Place this script under the master Artifactory server ${ARTIFACTORY_HOME}/etc/plugins.
 *   1.5. Verify in the ${ARTIFACTORY_HOME}/logs/artifactory.log that the plugin loaded the configuration correctly.
 * 2. Executing Pull Replication:
 *  2.1. The plugin may run a pull configured key from buildSync.json
 *  2.2. [WIP] The plugin can receive a full JSON content for pull, like:
 *       {
 *          "sourceUrl": "http://us-east.acme.com/artifactory",
 *          "sourceUser": "buildSynchronizer",
 *          "sourcePassword": "XXXXXXX",
 *          "buildNames": ["productA :: nightly-release", "productB :: nightly-release"],
 *          "delete": false,
 *          "activatePlugins": false,
 *          "reinsert": false
 *      },
 *
 *   2.3. Call the predefined pull config through the plugin execution by running:
 *    curl -X POST -v -u admin:password "http://localhost:8080/artifactory/api/plugins/execute/buildSyncPullConfig?params=key=ABRelease"
 *
 *   2.4. [WIP] You can pass the full JSON for the plugin in the REST call:
 *    curl -X POST -v -u admin:password -H "Content-Type: application/json" -T pullEast.json "http://localhost:8080/artifactory/api/plugins/execute/buildSyncPullJson"
 *
 * 3. Executing Push Replication:
 *  3.1. The plugin may run a push configured key from buildSync.json
 *  3.2. [WIP] The plugin can receive a full JSON content for push, like:
 *       {
 *          "destinationUrl": "http://us-west.acme.com/artifactory",
 *          "destinationUser": "buildSynchronizer",
 *          "destinationPassword": "XXXXXXX",
 *          "buildNames": ["*nightly-release"],
 *          "delete": true
 *      },
 *
 *   3.3. Call the predefined push config through the plugin execution by running:
 *    curl -X POST -v -u admin:password "http://localhost:8080/artifactory/api/plugins/execute/buildSyncPushConfig?params=key=ABPush"
 *
 *   3.4. [WIP] You can pass the full JSON for the plugin in the REST call:
 *    curl -X POST -v -u admin:password -H "Content-Type: application/json" -T pushWest.json "http://localhost:8080/artifactory/api/plugins/execute/buildSyncPushJson"
 *
 */

@Grapes([
@Grab(group = 'org.codehaus.groovy.modules.http-builder', module = 'http-builder', version = '0.6')
])
@GrabExclude('commons-codec:commons-codec')

import groovy.json.JsonSlurper
import groovyx.net.http.HTTPBuilder
import org.apache.http.HttpRequestInterceptor
import org.apache.http.StatusLine
import org.artifactory.addon.AddonsManager
import org.artifactory.addon.plugin.PluginsAddon
import org.artifactory.addon.plugin.build.AfterBuildSaveAction
import org.artifactory.addon.plugin.build.BeforeBuildSaveAction
import org.artifactory.api.jackson.JacksonReader
import org.artifactory.api.rest.build.BuildInfo
import org.artifactory.build.BuildRun
import org.artifactory.build.Builds
import org.artifactory.build.DetailedBuildRun
import org.artifactory.build.DetailedBuildRunImpl
import org.artifactory.exception.CancelException
import org.artifactory.rest.resource.ha.BuildRunImpl
import org.artifactory.storage.build.service.BuildStoreService
import org.artifactory.storage.db.DbService
import org.artifactory.util.HttpUtils
import org.jfrog.build.api.Build
import org.slf4j.Logger

import java.util.concurrent.Callable

import static groovyx.net.http.ContentType.BINARY
import static groovyx.net.http.ContentType.JSON
import static groovyx.net.http.Method.DELETE
import static groovyx.net.http.Method.PUT

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
            PullConfig pullConf = baseConf.pullConfigs[confKey]
            doSync(
                    new RemoteBuildService(pullConf.source, log),
                    new LocalBuildService(ctx, log, pullConf.reinsert, pullConf.activatePlugins),
                    pullConf.buildNames,
                    pullConf.delete
            )

            status = 200
            message = "Builds ${pullConf.buildNames} from ${pullConf.source.url} were successfully replicated\n"

        } catch (Exception e) {
            //aborts during execution
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
            PushConfig pushConf = baseConf.pushConfigs[confKey]
            def localBuildService = new LocalBuildService(ctx, log, false, false)

            pushConf.destinations.each { destServer ->
                doSync(
                        localBuildService,
                        new RemoteBuildService(destServer, log),
                        pushConf.buildNames,
                        pushConf.delete
                )
            }

            status = 200
            message = "Builds ${pushConf.buildNames} were successfully replicated to ${pushConf.destinations.collect { it.url }}\n"

        } catch (Exception e) {
            //aborts during execution
            log.error("Failed pull config", e)
            status = 500
            message = e.message
        }
    }
}

def doSync(BuildListBase src, BuildListBase dest, List<String> buildNames, boolean delete) {
    List res = []
    Set<BuildRun> buildNamesToSync = src.filterBuildNames(buildNames)
    buildNamesToSync.each { BuildRun srcBuild ->
        if (srcBuild.started && srcBuild.started == dest.getLastStarted(srcBuild.name)) {
            log.debug "Build ${srcBuild} is already sync!"
        } else {
            String buildName = srcBuild.name
            def srcBuilds = src.getBuildNumbers(buildName)
            def destBuilds = dest.getBuildNumbers(buildName)
            def common = srcBuilds.intersect(destBuilds)
            log.info "Found ${common.size()} identical builds"
            destBuilds.removeAll(common)
            srcBuilds.removeAll(common)
            srcBuilds.each { BuildRun b ->
                Build buildInfo = src.getBuildInfo(b)
                if (buildInfo) {
                    dest.addBuild(buildInfo)
                }
            }
            if (delete) {
                destBuilds.each { BuildRun b ->
                    dest.deleteBuild(b)
                }
            }
        }
    }
    res
}

abstract class BuildListBase {
    def log
    private Set<BuildRun> _allLatestBuilds = null

    BuildListBase(log) { this.log = log }

    Set<BuildRun> getAllLatestBuilds() {
        if (_allLatestBuilds == null) {
            _allLatestBuilds = loadAllBuilds()
        }
        return _allLatestBuilds
    }

    abstract Set<BuildRun> loadAllBuilds();

    Set<BuildRun> filterBuildNames(List<String> buildNames) {
        Set<BuildRun> result = new HashSet<>()
        buildNames.each { String buildName ->
            if (buildName.contains('*')) {
                result.addAll(getAllLatestBuilds().findAll { it.name.matches(buildName) })
            } else {
                BuildRun found = getAllLatestBuilds().find { it.name == buildName }
                if (!found) {
                    found = new BuildRunImpl(buildName, "", "")
                }
                result << found
            }
        }
        result
    }

    String getLastStarted(String buildName) {
        getAllLatestBuilds().find { it.name == buildName }?.started
    }

    abstract Set<BuildRun> getBuildNumbers(String buildName);

    abstract Build getBuildInfo(BuildRun b);

    abstract def addBuild(Build buildInfo);

    abstract def deleteBuild(BuildRun buildRun);
}

class RemoteBuildService extends BuildListBase {
    Server server
    HTTPBuilder http
    StatusLine lastFailure = null
    int majorVersion, minorVersion

    RemoteBuildService(Server server, log) {
        super(log)
        this.server = server
        http = new HTTPBuilder(server.url)
        //http.auth.basic(server.user, server.password)
        http.client.addRequestInterceptor({ def httpRequest, def httpContext ->
            httpRequest.addHeader('Authorization',
                    "Basic ${"${server.user}:${server.password}".getBytes().encodeBase64()}")
        } as HttpRequestInterceptor)
        http.handler.failure = { resp ->
            lastFailure = resp.statusLine
        }
        log.info "Extracting Artifactory version from ${server.url}api/system/version"
        http.get(contentType: JSON, path: "api/system/version") { resp, json ->
            log.info "Got Artifactory version ${json.version} and license ${json.license} from ${server.url}"
            def v = json.version.tokenize('.')
            if (v.size() < 3) {
                throw new CancelException("Server ${server.url} version not correct. Got ${json.text}",
                        500)
            }
            majorVersion = v[0] as int
            minorVersion = v[1] as int
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
    Set<BuildRun> loadAllBuilds() {
        lastFailure = null
        Set<BuildRun> result = new HashSet<>()
        log.info "Getting all builds from ${server.url}api/build"
        http.get(contentType: JSON, path: 'api/build') { resp, json ->
            json.builds.each { b ->
                result << new BuildRunImpl(
                        HttpUtils.decodeUri(b.uri.substring(1)),
                        "LATEST",
                        b.lastStarted)
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
    Set<BuildRun> getBuildNumbers(String buildName) {
        lastFailure = null
        Set<BuildRun> result = new HashSet<>()
        log.info "Getting all build numbers from ${server.url}api/build/$buildName"
        http.get(contentType: JSON, path: "api/build/$buildName") { resp, json ->
            json.buildsNumbers.each { b ->
                result << new BuildRunImpl(buildName,
                        HttpUtils.decodeUri(b.uri.substring(1)),
                        b.started)
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
        Map<String, String> queryParams = [started: b.started]
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
            uri.path = "api/build"
            //JsonGenerator jsonGenerator = JacksonFactory.createJsonGenerator(outputStream);
            //jsonGenerator.writeObject(buildInfo);
            body = buildInfo
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
            response.success = {
                log.info "Successfully deleted builds ${buildInfo.name}/${buildInfo.number}"
            }
        }
        if (lastFailure != null) {
            def msg = "Could not delete build ${buildInfo.name}/${buildInfo.number} in ${server.url} got: ${lastFailure.reasonPhrase}"
            log.warn msg
        }
    }
}

class LocalBuildService extends BuildListBase {
    DbService dbService
    BuildStoreService buildStoreService
    AddonsManager addonsManager
    PluginsAddon pluginsAddon
    Builds builds
    boolean reinsert
    boolean activatePlugins

    LocalBuildService(ctx, log, reinsert, activatePlugins) {
        super(log)
        dbService = ctx.beanForType(DbService.class)
        addonsManager = ctx.beanForType(AddonsManager.class)
        pluginsAddon = addonsManager.addonByType(PluginsAddon.class);
        buildStoreService = ctx.beanForType(BuildStoreService.class)
        builds = ctx.beanForType(Builds.class)
        this.reinsert = reinsert
        this.activatePlugins = activatePlugins
    }

    @Override
    Set<BuildRun> loadAllBuilds() {
        def res = buildStoreService.getLatestBuildsByName().collect {
            new BuildRunImpl(it.name, "LATEST", it.started)
        }
        log.info "Found ${res.size()} builds locally"
        res
    }

    @Override
    Set<BuildRun> getBuildNumbers(String buildName) {
        def res = buildStoreService.findBuildsByName(buildName).collect { new BuildRunImpl(it) }
        log.info "Found ${res.size()} local builds named $buildName"
        res
    }

    @Override
    def addBuild(Build buildInfo) {
        try {
            log.info "Deploying locally build ${buildInfo.name}:${buildInfo.number}:${buildInfo.started}"
            DetailedBuildRun detailedBuildRun = new DetailedBuildRunImpl(buildInfo);
            if (reinsert) {
                builds.saveBuild(detailedBuildRun)
            } else {
                dbService.invokeInTransaction(new Callable<Object>() {
                    @Override
                    public Object call() throws Exception {
                        if (activatePlugins) {
                            pluginsAddon.execPluginActions(BeforeBuildSaveAction.class, builds, detailedBuildRun);
                        }
                        buildStoreService.addBuild(buildInfo);
                        if (activatePlugins) {
                            pluginsAddon.execPluginActions(AfterBuildSaveAction.class, builds, detailedBuildRun);
                        }
                        return null;
                    }
                });
            }
        } catch (Exception e) {
            String message = "Insertion of build  ${buildInfo.name}:${buildInfo.number}:${buildInfo.started} failed due to: ${e.getMessage()}"
            log.warn(message, e)
        }
    }

    @Override
    def deleteBuild(BuildRun buildRun) {
        try {
            log.info "Deleting local build $buildRun"
            builds.deleteBuild(buildRun)
        } catch (Exception e) {
            String message = "Deletion of build ${buildRun} failed due to: ${e.getMessage()}"
            log.warn(message, e)
        }
    }

    @Override
    Build getBuildInfo(BuildRun b) {
        return buildStoreService.getBuildJson(b)
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
        this.confFile = new File("$ctx.artifactoryHome.etcDir/plugins", "buildSync.json")
    }

    BaseConfiguration getCurrent() {
        log.debug "Retrieving current conf $confFileLastChecked $confFileLastModified $current"
        if (current == null || needReload()) {
            log.info "Current conf reloading"
            if (!confFile || !confFile.exists()) {
                errors = [ "The conf file ${confFile.getAbsolutePath()} does not exists!" ]
            } else {
                try {
                    current = new BaseConfiguration(confFile,log)
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
    Map<String, Server> servers = [:]
    Map<String, PullConfig> pullConfigs = [:]
    Map<String, PushConfig> pushConfigs = [:]

    BaseConfiguration(File confFile, log) {
        def reader
        try {
            reader = new FileReader(confFile)
            def slurper = new JsonSlurper().parse(reader)
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

    PullConfig(def slurp, Map<String, Server> servers) {
        key = slurp.key
        sourceKey = slurp.source
        source = servers.get(sourceKey)
        buildNames = slurp.buildNames as List
        delete = slurp.delete as boolean
        reinsert = slurp.reinsert as boolean
        activatePlugins = slurp.activatePlugins as boolean
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

