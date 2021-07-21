/*
 * Copyright (C) 2018 JFrog Ltd.
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

import com.google.common.collect.BiMap
import com.google.common.collect.ImmutableBiMap
import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import java.util.regex.Pattern
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
/**
 * Webhook for Artifactory
 *
 * This webhook includes the following components
 * 1. webhook.groovy - main script, modify only if needing to change functionality
 * 2. webhook.config.json - specify the target url and event to trigger webhook
 *
 * Installation:
 * 1. Copy webhook.config.json.sample to webhook.config.json, configure and copy to ARTIFACTORY_HOME/etc/plugins
 * 2. add webook.groovy to <artifactory.home>/etc/plugins, artifactory should log: Script 'webhook' loaded.
 *
 */

/**
 * Supported events
 */
class Globals {

    static final SUPPORT_MATRIX = [
        "storage": [
            "afterCreate": [ name: "storage.afterCreate", description: "Called after artifact creation operation",
                             humanName: "Artifact created"],
            "afterDelete": [ name: "storage.afterDelete", description: "Called after artifact deletion operation",
                             humanName: "Artifact deleted"],
            "afterMove": [ name: "storage.afterMove", description: "Called after artifact move operation",
                           humanName: "Artifact moved"],
            "afterCopy": [ name: "storage.afterCopy", description: "Called after artifact copy operation",
                           humanName: "Artifact copied"],
            "afterPropertyCreate": [ name: "storage.afterPropertyCreate",
                                     description: "Called after property create operation",
                                     humanName: "Property created"],
            "afterPropertyDelete": [ name: "storage.afterPropertyDelete",
                                     description: "Called after property delete operation",
                                     humanName: "Property deleted"],
        ],
        "build": [
            "afterSave": [ name: "build.afterSave", description: "Called after a build is deployed",
                           humanName: "Build published"]
        ],
        "execute": [
            "pingWebhook": [ name: "execute.pingWebhook",
                             description: "Simple test call used to ping a webhook endpoint",
                             humanName: "Webhook ping test"]
        ],
        "docker": [
            "tagCreated": [ name: "docker.tagCreated", description: "Called after a tag is created",
                            humanName: "Docker tag created"],
            "tagDeleted": [ name: "docker.tagDeleted", description: "Called after a tag is deleted",
                            humanName: "Docker tag deleted"]
        ]
    ]

    static final RESPONSE_FORMATTER_MATRIX = [
        default: [
            description: "The default formatter", formatter: new ResponseFormatter ( )
        ],
        keel: [
            description: "A POST formatted specifically for keel.sh", formatter: new KeelFormatter ( )
        ],
        slack: [
            description: "A POST formatted specifically for Slack", formatter: new SlackFormatter ( )
        ],
        spinnaker: [
                description: "A POST formatted specifically for Spinnaker", formatter: new SpinnakerFormatter ()
        ]
    ]

    // Simple access to the actual supported events
    static SUPPORTED_EVENTS = [ ].toSet ( )
    static {
        SUPPORT_MATRIX.each {
            k, v ->
                v.each { k1, v1 ->
                    SUPPORTED_EVENTS.add(v1.name)
                }
        }
    }
    /**
     * Get the support matrix entry for a particular event
     * @param event
     * @return
     */
    static def eventToSupported(String event) {
        def idx = event.indexOf('.')
        return SUPPORT_MATRIX[event.substring(0, idx)][event.substring(idx + 1)]
    }
    enum PackageTypeEnum {
        HELM("helm/chart"),
        DOCKER ("docker/image")

        private String value

        PackageTypeEnum(String value) {
            this.value = value
        }

        String toString() {
            return value;
        }
    }
    static final BiMap<String, PackageTypeEnum> PACKAGE_TYPE_MAP = new ImmutableBiMap.Builder<String, PackageTypeEnum>()
            .put("helm", PackageTypeEnum.HELM)
            .put("docker", PackageTypeEnum.DOCKER)
            .build()
    static repositories
}

WebHook.init(ctx, log)

/**
 * REST APIs for the webhook
 */
executions {

    Globals.repositories = repositories

    /**
     * Simple PING event to test endpoint
     */
    pingWebhook(httpMethod: 'GET') {
        def json = new JsonBuilder()
        json (
                message: "It works!",
        )
        hook(Globals.SUPPORT_MATRIX.execute.pingWebhook.name, json)
    }

    /**
     * Reload the webhook configuration file
     */
    webhookReload (httpMethod: 'POST') {
        WebHook.reload()
        message = "Reloaded!\n"
    }

    /**
     * Get a list of supported events and formatters along with a brief description
     */
    webhookInfo(httpMethod: 'GET') {
        def sb = ''<<''
        sb <<= 'Artifactory Webhook Supported Events\n'
        sb <<= '-----------------\n\n'
        Globals.SUPPORT_MATRIX.each { k, v ->
            sb <<= '### ' << k.capitalize() << '\n'
            v.each { k1, v1 ->
                sb <<= "${v1.name} - ${v1.description}\n"
            }
            sb <<= '\n'
        }
        sb <<= '\n\nArtifactory Webhook Formatters\n'
        sb <<= '-----------------\n\n'
        Globals.RESPONSE_FORMATTER_MATRIX.each { k, v ->
            sb <<= "${k} - ${v.description}\n"
        }
        sb <<= '\n'
        message = sb.toString()
    }

}

/**
 * Listen to storage events
 */
storage {
    /**
     * Handle after create events.
     *
     * Closure parameters:
     * item (org.artifactory.fs.ItemInfo) - the original item being created.
     */
    afterCreate { item ->
        hook(Globals.SUPPORT_MATRIX.storage.afterCreate.name, item ? new JsonBuilder(item) : null)
    }

    /**
     * Handle after delete events.
     *
     * Closure parameters:
     * item (org.artifactory.fs.ItemInfo) - the original item deleted.
     */
    afterDelete { item ->
        hook(Globals.SUPPORT_MATRIX.storage.afterDelete.name, item ? new JsonBuilder(item) : null)
    }

    /**
     * Handle after move events.
     *
     * Closure parameters:
     * item (org.artifactory.fs.ItemInfo) - the source item moved.
     * targetRepoPath (org.artifactory.repo.RepoPath) - the target repoPath for the move.
     */
    afterMove { item, targetRepoPath, properties ->
        def json = new JsonBuilder()
        json (
                item: item,
                targetRepoPath: targetRepoPath,
                user: security.getCurrentUsername(),
                properties: properties
        )
        hook(Globals.SUPPORT_MATRIX.storage.afterMove.name, json)
    }

    /**
     * Handle after copy events.
     *
     * Closure parameters:
     * item (org.artifactory.fs.ItemInfo) - the source item copied.
     * targetRepoPath (org.artifactory.repo.RepoPath) - the target repoPath for the copy.
     */
    afterCopy { item, targetRepoPath, properties ->
        def json = new JsonBuilder()
        json (
                item: item,
                targetRepoPath: targetRepoPath,
                user: security.getCurrentUsername(),
                properties: properties
        )
        hook(Globals.SUPPORT_MATRIX.storage.afterCopy.name, json)
    }

    /**
     * Handle after property create events.
     *
     * Closure parameters:
     * item (org.artifactory.fs.ItemInfo) - the item on which the property has been set.
     * name (java.lang.String) - the name of the property that has been set.
     * values (java.lang.String[]) - A string array of values assigned to the property.
     */
    afterPropertyCreate { item, name, values ->
        def json = new JsonBuilder()
        json (
                item: item,
                name: name,
                values: values,
                user: security.getCurrentUsername()
        )
        hook(Globals.SUPPORT_MATRIX.storage.afterPropertyCreate.name, json)
    }

    /**
     * Handle after property delete events.
     *
     * Closure parameters:
     * item (org.artifactory.fs.ItemInfo) - the item from which the property has been deleted.
     * name (java.lang.String) - the name of the property that has been deleted.
     */
    afterPropertyDelete { item, name ->
        def json = new JsonBuilder()
        json (
                item: item,
                name: name,
                user: security.getCurrentUsername()
        )
        hook(Globals.SUPPORT_MATRIX.storage.afterPropertyDelete.name, json)
    }
}

/**
 * Listen to build events
 */
build {
    /**
     * Handle after build info save events
     *
     * Closure parameters:
     * buildRun (org.artifactory.build.DetailedBuildRun) - Build Info that was saved. Partially mutable.
     */
    afterSave { buildRun ->
        hook(Globals.SUPPORT_MATRIX.build.afterSave.name, new JsonBuilder(buildRun))
    }
}

/**
 * Default formatter
 */
class ResponseFormatter {
    def format(String event, JsonBuilder data) {
        def builder = new JsonBuilder()
        builder.artifactory {
            webhook(
                    event: event,
                    data: data.content
            )
        }
        return builder
    }
}

/**
 * Keel formatter
 */
class KeelFormatter {
    def format(String event, JsonBuilder data) {
        def builder = new JsonBuilder()
        def json = data.content
        def imagePath 
        if (WebHook.dockerRegistryUrl()) {
          imagePath = "${WebHook.dockerRegistryUrl()}/${json.docker.image}"
        } else {
          imagePath = "${WebHook.baseUrl()}/${json.repoKey}/${json.docker.image}"
        }
        builder {
          name "${imagePath}"
          tag json.docker.tag
        }
        return builder
    }
}

/**
 * Spinnaker formatter
 */
class SpinnakerFormatter {
    def format(String event, JsonBuilder data) {
        def eventTypeMetadata = Globals.eventToSupported(event)
        def builder = new JsonBuilder()
        def json = data.content

        if (Globals.SUPPORT_MATRIX.storage.afterCreate.name == eventTypeMetadata.name) {
            def type = getPackageType(json.repoKey)

            if(Globals.PackageTypeEnum.HELM.toString() == type) {
                def nameVersionDetails = getHelmPackageNameAndVersion(json)
                builder {
                    artifacts(
                            [
                                [
                                    type     : type,
                                    name     : nameVersionDetails.name,
                                    version  : nameVersionDetails.version,
                                    reference: "${WebHook.baseUrl()}/${json.repoKey}/${json.relPath}"
                                ]
                            ]
                    )
                }
            }else{
                builder {
                    text "Artifactory: ${eventTypeMetadata['humanName']} event is only support for HELM repository by Spinnaker formatter"
                }
            }
        }else if (Globals.SUPPORT_MATRIX.docker.tagCreated.name == eventTypeMetadata.name) {
            builder {
                artifacts(
                     [
                         [
                                 type: getPackageType(json.event.repoKey),
                                 name: json.docker.image,
                                 version: json.docker.tag,
                                 reference: "${WebHook.baseUrl()}/${json.event.repoKey}/${json.docker.image}:${json.docker.tag}"
                         ]
                     ]
                )
            }
        } else{
            builder {
                text "Artifactory: ${eventTypeMetadata['humanName']} event is not supported by Spinnaker formatter"
            }
        }
        return builder
    }

    def getPackageType(repoKey) {
        def repoInfo = Globals.repositories.getRepositoryConfiguration(repoKey)
        def packageType = Globals.PACKAGE_TYPE_MAP.get(repoInfo.getPackageType()).toString()
        return packageType
    }

    def getHelmPackageNameAndVersion(json) {
        def map
        String name = null
        String version = null
        if (json.name) {
            def m = (json.name.split(/\-\d+\./))

            if (m && m.size() > 1) {
                name = m[0]
                version = json.name.substring(name.length() + 1, json.name.lastIndexOf('.'))
            }
        }
        map = [name: name, version: version]
        return map
    }
}

/**
 * Slack formatter
 */
class SlackFormatter {
    def format(String event, JsonBuilder data) {
        def eventTypeMetadta = Globals.eventToSupported(event)
        def builder = new JsonBuilder()

        def json = data.content
        def detailsMap = getDetails(eventTypeMetadta, json)
        builder {
            text "Artifactory: Event triggered by ${eventTypeMetadta['humanName']}"
            attachments (
                [
                    [
                        fallback : "Details",
                        fields: detailsMap
                    ],
                ]
            )
        }
        return builder
    }

    def getDetails(eventMetadata, json) {
        def map = []
        try {
            if (eventMetadata.name.startsWith("build")) {
                map << [short: false, title: "User", "value": json.artifactoryPrincipal]
                map << [short: false, title: "Build", "value": "${json.name} - ${json.number}"]
            } else if (eventMetadata.name.startsWith("docker")) {
                map << [short: false, title: "User", "value": json.event.modifiedBy]
                map << [short: false, title: "Path", "value": "${json.docker.image}:${json.docker.tag}"]
                map << [short: false, title: "Repository", "value": json.event.repoKey]
            } else if (eventMetadata.name.startsWith("storage")) {
                if (Globals.SUPPORT_MATRIX.storage.afterMove.name == eventMetadata.name ||
                        Globals.SUPPORT_MATRIX.storage.afterCopy.name == eventMetadata.name) {
                    map << [short: false, title: "User", "value": json.user]
                    map << [short: false, title: "From", "value": "${json.item.repoKey}/${json.item.relPath}"]
                    map << [short: false, title: "To", "value": "${json.targetRepoPath.repoKey}/${json.targetRepoPath.path}"]
                } else if (Globals.SUPPORT_MATRIX.storage.afterPropertyCreate.name == eventMetadata.name ||
                        Globals.SUPPORT_MATRIX.storage.afterPropertyDelete.name == eventMetadata.name) {
                    map << [short: false, title: "User", "value": json.user]
                    map << [short: false, title: "Path", "value": json.item.relPath]
                    map << [short: false, title: "Repository", "value": json.item.repoKey]
                    map << [short: false, title: "Name", "value": json.name]
                    if (json.values)
                        map << [short: false, title: "Values", "value": json.values.toString()]
                } else {
                    map << [short: false, title: "User", "value": json.modifiedBy]
                    map << [short: false, title: "Path", "value": json.relPath]
                    map << [short: false, title: "Repository", "value": json.repoKey]
                }
            }
        } catch (ex) {
            // Should not happen, but if it does, at least send something
        }
        return map
    }
}

/**
 * Adds some additional information to the data payload
 * @param data The original payload
 * @return The modified payload
 */
def dockerDataDecorator(JsonBuilder data) {
    def tagName = null, imageName = null
    def path = data.content.relPath
    def m = path =~ /^(.*)\/(.*?)\/manifest.json$/
    if (m[0] && m[0].size() == 3)
    {
        imageName = m[0][1]
        tagName = m[0][2]
    }
    def builder = new JsonBuilder()
    builder {
        docker(
            [
                "tag": tagName,
                "image": imageName
            ]
        )
        event data.content
    }
    return builder

}
/**
 * Analyses basic Artifactory events and infers Docker specific events based on the data
 * @param event The event that triggered this
 * @param data The data associated with the event
 */
def dockerEventDecoratorWork(String event, JsonBuilder data) {
    def json = data.content
    def repoKey = json.repoKey
    def repoInfo = repositories.getRepositoryConfiguration(repoKey)
    if (repoInfo && repoInfo.isEnableDockerSupport())
        if (json.name && json.name == "manifest.json")
            hook(event, dockerDataDecorator(data))
}

/**
 * Analyses basic Artifactory events and infers Docker specific events based on the data
 * @param event The event that triggered this
 * @param data The data associated with the event
 */
def dockerEventDecorator(String event, JsonBuilder data) {
    // New tag creation
    if (event == Globals.SUPPORT_MATRIX.storage.afterCreate.name)
        dockerEventDecoratorWork(Globals.SUPPORT_MATRIX.docker.tagCreated.name, data)
    // Tag deletion
    if (event == Globals.SUPPORT_MATRIX.storage.afterDelete.name)
        dockerEventDecoratorWork(Globals.SUPPORT_MATRIX.docker.tagDeleted.name, data)
}

/**
 * hook method for script invocation
 * @param event
 * @param data
 * @return
 */
def hook(String event, JsonBuilder data) {
    try {
        if (WebHook.failedToLoadConfig) {
            log.error("Failed to load configuration from webhook.config.json. Verify that it is valid JSON.")
            return
        }
        if (Globals.SUPPORTED_EVENTS.contains(event) && WebHook.active(event)) {
            log.trace(data.toString())
            log.info("Webhooks being triggered for event '${event}'")
            WebHook.run(event, data)
        }
        // Docker decorator should occur after the basic event  even if we don't care about the basic event
        dockerEventDecorator(event, data)
    } catch (Exception ex) {
        // Don't risk failing the event by throwing an exception
        if (WebHook.debug())
            ex.printStackTrace()
        log.error("Webhook threw an exception: " + ex.getMessage())
    }
}

/**
 * WebHook class for handling grunt work
 * config load webhook.properties upon startup or REST API execute/webhookReload
 */
class WebHook {
    private static WebHook me
    private static final int MAX_TIMEOUT = 60000
    private static final String REPO_KEY_NAME = "repoKey"
    private static final responseFormatters = Globals.RESPONSE_FORMATTER_MATRIX
    public static boolean failedToLoadConfig = false
    def triggers = new HashMap()
    def debug = false
    def connectionTimeout = 15000
    def baseUrl
    def dockerRegistryUrl
    def ctx = null
    def log = null
    // Used for async POSTS
    ExecutorService excutorService = Executors.newFixedThreadPool(10)

    /**
     * Sends the event to the particular webhooks for filtering and processing
     * @param event The event that triggered the webhook
     * @param json The JSON formatted information of the event
     */
    static void run(String event, Object json) {
        me.process(event, json)
    }


    /**
     * Determines if a certain event has webhooks listenting to it
     * @param event The event
     * @return True if there is at least one webhook listening for the particular event
     */
    static boolean active(String event) {
        if (me.triggers.get(event)) {
            return true
        } else {
            return false
        }
    }

    /**
     * Determines if we are in debug mode
     * @return True if the debug flag is set
     */
    static boolean debug() {
        return me != null && me.debug == true
    }

    /**
     * Get the baseUrl value
     * @return value if the baseUrl value is set
     */
    static String baseUrl() {
        return me.baseUrl
    }

    /**
     * Get the dockerRegistryUrl value
     * @return value if the dockerRegistryUrl value is set
     */
    static String dockerRegistryUrl() {
        return me.dockerRegistryUrl
    }

    /**
     * Determine which formatter to use for the body
     * @param json The unformatted JSON
     * @param event The event that triggered this
     * @param webhook The webhook details that specify which formatter should be used
     * @return The string version of the body
     */
    private String getFormattedJSONString(JsonBuilder json, String event, WebhookEndpointDetails webhook) {
        if (webhook.isDefaultFormat() || !responseFormatters.containsKey(webhook.format)) {
            return (responseFormatters['default'].formatter.format(event, json)).toString()
        }
        return (responseFormatters[webhook.format].formatter.format(event, json)).toString()
    }

    /**
     * Triggers all the webhooks listening to a particular event
     * @param event The event that triggered this call
     * @param json The JSON formatted information of the event
     */
    private void process(String event, Object json) {
        if (active(event)) {
            def webhookListeners = triggers.get(event)
            if (webhookListeners) {
                // We need to do this twice to do all async first
                for (WebhookEndpointDetails webhookListener : webhookListeners) {
                    try {
                        if (webhookListener.isAsync()) {
                            if (eventPassedFilters(event, json, webhookListener))
                                excutorService.execute(
                                        new PostTask(webhookListener.url, getFormattedJSONString(json, event, webhookListener)))
                        }
                    } catch (Exception e) {
                        // We don't capture async results
                        if (debug)
                            e.printStackTrace()
                    }
                }
                for (def webhookListener : webhookListeners) {
                    try {
                        if (!webhookListener.isAsync())
                            if (eventPassedFilters(event, json, webhookListener))
                                callPost(webhookListener.url, getFormattedJSONString(json, event, webhookListener))
                    } catch (Exception e) {
                        if (debug)
                            e.printStackTrace()
                    }
                }
            }
        }
    }

    private boolean eventPassedFilters(String event, Object json, WebhookEndpointDetails webhook) {
        return eventPassedDirectoryFilter(event, json) && eventPassedRepositoryAndPathFilter(event, json, webhook)
    }

    /**
     * Determines if the particular event passes any repository/path filter on the webhook
     * @param event The event that triggered this call
     * @param json The JSON formatted information of the event
     * @param webhook The specific webhook
     * @return False only if the event is of type storage/docker and the webhook has a repo/path filter which does not
     * include the particular repository(s)/path(s) involved in this event
     */
    private boolean eventPassedRepositoryAndPathFilter(String event, Object json, WebhookEndpointDetails webhook) {
        boolean passesFilter = true
        def jsonData = null // Don't slurp unless necessary to avoid overhead
        if (event.startsWith("storage") || event.startsWith("docker")) {
            // Check repo if needed
            if (!webhook.allRepos()) {
                jsonData = new JsonSlurper().parseText(json.toString())
                def reposInEvent = findDeep(jsonData, REPO_KEY_NAME)
                boolean found = false
                if (reposInEvent != null && reposInEvent.size() > 0) {
                    reposInEvent.each {
                        if (webhook.appliesToRepo(it))
                            found = true
                    }
                }
                passesFilter = found
            }
            if (passesFilter && webhook.hasPathFilter()) { // Check path if needed
                if (jsonData == null)
                    jsonData = new JsonSlurper().parseText(json.toString())
                def matches = false
                if (event.startsWith("docker")) {
                    matches = webhook.matchesPathFilter(jsonData.docker.image) &&
                        webhook.matchesTagFilter(jsonData.docker.tag)
                } else if (event.startsWith("storage")) {
                    if (Globals.SUPPORT_MATRIX.storage.afterMove.name == event ||
                            Globals.SUPPORT_MATRIX.storage.afterCopy.name == event) {
                        matches = webhook.matchesPathFilter(jsonData.item.relPath) ||
                                webhook.matchesPathFilter(jsonData.targetRepoPath.relPath)
                    } else if (Globals.SUPPORT_MATRIX.storage.afterPropertyCreate.name == event ||
                            Globals.SUPPORT_MATRIX.storage.afterPropertyDelete.name == event) {
                        matches =  webhook.matchesPathFilter(jsonData.item.relPath)
                    } else {
                        matches =  webhook.matchesPathFilter(jsonData.relPath)
                    }
                }
                passesFilter = matches
            }
        }
        return passesFilter
    }

    /**
     * Filter out create/delete events for directories
     * @param event The event that triggered this call
     * @param json The JSON formatted information of the event
     * @return True if this is NOT a directory create/delete event
     */
    private boolean eventPassedDirectoryFilter(String event, Object object) {
        if (event.startsWith("storage")) {
            def json = object.content
            if (json.folder)
                return false
        }
        return true
    }

    /**
     * Performs the actual POST request
     * @param urlString The remote site to POST to
     * @param content The JSON formatted body for the POST
     * @return The response/error code from the remote site
     */
    private String callPost(String urlString, String content) {
        def url = new URL(urlString)
        def post = url.openConnection()
        post.method = "POST"
        post.doOutput = true
        post.setConnectTimeout(connectionTimeout)
        post.setReadTimeout(connectionTimeout)
        post.setRequestProperty("Content-Type", "application/json")
        def authidx = url.authority.indexOf('@')
        if (authidx > 0) {
            def auth = url.authority[0..<authidx].bytes.encodeBase64()
            post.setRequestProperty("Authorization", "Basic $auth")
        }
        def writer = null, reader = null
        try {
            writer = post.outputStream
            writer.write(content.getBytes("UTF-8"))
            writer.flush()
            def postRC = post.getResponseCode()
            def response = postRC
            if (postRC.equals(200)) {
                reader = post.inputStream
                response = reader.text
            }
            return response
        } finally {
            if (writer != null)
                writer.close()
            if (reader != null)
                reader.close()
        }
    }

    /**
     * Reloads the configuration, discarding the previous one.
     */
    static void reload() {
        def tctx = null
        def tlog = null
        if (me != null) {
            tctx = me.ctx
            tlog = me.log
            me.excutorService.shutdown()
            me = null
        }
        init(tctx, tlog)
    }

    /**
     * Initializes the webhook
     */
    synchronized static void init(ctx, log) {
        if (me == null) {
            me = new WebHook()
            me.ctx = ctx
            me.log = log
            failedToLoadConfig = false
            try {
                me.loadConfig()
            } catch (ex) {
                failedToLoadConfig = true
                me = null
            }
        }
    }

    /**
     * Loads and processes the configuration file
     */
    private void loadConfig() {
        final String CONFIG_FILE_PATH = "${ctx.artifactoryHome.etcDir}/plugins/webhook.config.json"
        def inputFile = new File(CONFIG_FILE_PATH)
        def config = new JsonSlurper().parseText(inputFile.text)
        if (config && config.webhooks) {
            config.webhooks.each { name, webhook ->
                loadWebhook(webhook)
            }
            // Potential debug flag
            if (config.containsKey("debug"))
                me.debug = config.debug == true
            // Timeout
            if (config.containsKey("timeout") && config.timeout > 0 && config.timeout <= MAX_TIMEOUT)
                me.connectionTimeout = config.timeout
            // BaseUrl
            if (config.containsKey("baseurl"))
                me.baseUrl = config.baseurl
            // DockerRegistryUrl
            if (config.containsKey("dockerRegistryUrl"))
                me.dockerRegistryUrl = config.dockerRegistryUrl
        }
    }


    /**
     * Loads a specific webhook from the properties file
     * @param cfg The specific webhook configuration to load
     */
    private void loadWebhook(Object cfg) {
        if (!cfg.containsKey("enabled") || cfg.enabled) {
            if (cfg.url) {
                if (cfg.events) {
                    // Registry the webhook details with an event (or set of events)
                    def webhookDetails = new WebhookEndpointDetails()
                    // Async flag
                    if (cfg.containsKey('async'))
                        webhookDetails.async = cfg.async
                    // Repositories
                    if (cfg.containsKey('repositories'))
                        webhookDetails.repositories = cfg.repositories
                    // Repositories
                    if (cfg.containsKey('format'))
                        webhookDetails.format = cfg.format
                    // Path filter
                    if (cfg.containsKey('path'))
                        webhookDetails.setPathFilter(cfg.path)
                    // Events
                    cfg.events.each {
                        webhookDetails.url = cfg.url
                        addWebhook(it, webhookDetails)
                    }
                }
            }
        }
    }

    /**
     * Adds a webhook endpoint to a specific event
     * @param event The name of the event the webhook should be activated on
     * @param details The details of webhook
     */
    private void addWebhook(String event, WebhookEndpointDetails webhookDetails) {
        def eventHooks = me.triggers.get(event)
        if (!eventHooks)
            me.triggers.put(event, [webhookDetails])
        else
            eventHooks.add(webhookDetails)
    }

    /**
     * Finds all values (recursively) in the JSON for any key
     * @param tree The JSON tree
     * @param key The key whose values will extract
     * @return A set of values
     */
    def findDeep(def tree, String key) {
        Set results = []
        findDeep(tree, key, results)
        return results
    }

    /**
     *
     * See findDeep(tree, string)
     **/
    def findDeep(def tree, String key, def results) {
        switch (tree) {
            case Map: tree.findAll { k, v ->
                if (v instanceof Map || v instanceof Collection)
                    findDeep(v, key, results)
                else if (k == key)
                    results.add(v)
            }
            case Collection:  tree.findAll { e ->
                if (e instanceof Map || e instanceof Collection)
                    findDeep(e, key, results)
            }
        }
    }

    /**
     * Holds details about a specific webhook endpoint
     */
    class WebhookEndpointDetails {
        public static String ALL_REPOS = "*"
        def url
        def format = null // Default format
        def repositories = [ALL_REPOS] // All
        def async = true
        Pattern path = null
        String tag = null

        boolean allRepos() {
            return repositories.contains(ALL_REPOS)
        }

        boolean appliesToRepo(String repoKey) {
            return allRepos() || repositories.contains(repoKey)
        }

        boolean isAsync() {
            return async
        }

        boolean isDefaultFormat() {
            return format == null || format == 'default'
        }

        /**
         * Whether or not this webhook has a path filter
         * @return true if and only if there is a path filter
         */
        boolean hasPathFilter() {
            return path != null
        }

        /**
         * Returns true if there is not a tag filter or if there is a matching tag
         * @param actualTag The tag in the event
         */
        boolean matchesTagFilter(String actualTag) {
            if(tag == null)
                return true
            return tag.equals(actualTag)
        }

        /**
         * Returns true if there is not a path filter or if there is and it matches the actual path
         * @param actualPath The path in the event
         */
        boolean matchesPathFilter(String actualPath) {
            if (path == null)
                return true
            return path.matcher(actualPath).matches()
        }

        /**
         * Set a path filter for the webhook to apply
         * @param searchString The user provided search string
         */
        void setPathFilter(String searchString) {
            // Remove leading '/'
            if (searchString.startsWith('/'))
                searchString = searchString.substring(1)
            //Set tag if one exists
            def tagIndex = searchString.indexOf(':')
            if (tagIndex >= 0 && tagIndex < searchString.length()-1) {
                tag = searchString.substring(tagIndex+1)
                searchString = searchString.substring(0, tagIndex)
            }
            path = Pattern.compile(regexFilter(searchString))
        }

        /**
         * Filters a user provided search text for a minimal regex search with the only supported character being '*'
         * NOTE: Not efficient but is only ran once when loading the config. Don't use where O(n).
         * @param search_string The unfiltered search string
         * @return The filtered search string
         */
        private String regexFilter(String searchString) {
            (['\\','.','[',']','{','}','(',')','<','>','+','-','=','?','^','$', '|']).each {
                searchString = searchString.replace(it, '\\' + it)
            }
            searchString = searchString.replace('*', '.*')
            return searchString
        }
    }

    /**
     * Simple task to asynchronously make the POST call
     */
    class PostTask implements Runnable {
        private String url
        private String content

        PostTask(String url, String content) {
            this.url = url
            this.content = content
        }

        void run() {
            callPost(url, content)
        }
    }
}
