import org.artifactory.addon.AddonsManager
import org.artifactory.addon.HaAddon
import org.artifactory.addon.plugin.PluginsAddon
import org.artifactory.api.security.UserGroupService
import org.artifactory.api.security.UserInfoBuilder
import org.artifactory.aql.AqlService
import org.artifactory.jaxb.JaxbHelper
import org.artifactory.repo.RepoPathFactory
import org.artifactory.request.RequestThreadLocal
import org.artifactory.resource.ResourceStreamHandle
import org.artifactory.schedule.CachedThreadPoolTaskExecutor
import org.artifactory.security.crypto.CryptoHelper
import org.artifactory.storage.db.servers.service.ArtifactoryServersCommonService
import org.artifactory.util.HttpClientConfigurator
import org.artifactory.util.HttpUtils

import org.apache.http.client.methods.*
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.HttpClients
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.transaction.support.TransactionSynchronizationManager

import groovy.json.JsonBuilder
import groovy.json.JsonException
import groovy.json.JsonSlurper

import java.io.IOException
import java.util.UUID

// The version of this plugin. When this file is updated, this number should be
// changed as well.
version = '1.0'

// Artifactory's thread pool, allowing for Xray events to run as background
// tasks.
threadPool = ctx.beanForType(CachedThreadPoolTaskExecutor)

// The event queue system. This is thread-safe, asynchronous, and allows for
// exactly one Xray event to run at a time.
eventMutex = new Object()
eventQueue = [] as Queue
eventProcessing = false

// The Xray config cache. A thread-safe, write-through cache for the Xray config
// file.
configMutex = new Object()
configData = null

// The HTTP client object, used to make connections to Xray. Initialized once,
// lazily.
httpclient = null

// Sets of filters that tell Xray whether to index a file. This is a map of
// package types to lists of file name patterns. All files in a repository that
// match any pattern under the repository's package type should be indexed.
xrayIndexers = [
  "generic": [
    "manifest.json", "*.rpm", "*.deb",
    "*.zip", "*.tar", "*.tgz", "*.gz", "*.nupkg", "*.apk",
    "*.jar", "*.war", "*.ear", "*.sar", "*.har", "*.hpi", "*.jpi"
  ],
  "maven": [
    "*.zip", "*.tar", "*.tgz", "*.gz", "*.nupkg", "*.apk",
    "*.jar", "*.war", "*.ear", "*.sar", "*.har", "*.hpi", "*.jpi"
  ],
  "docker": ["manifest.json"],
  "yum": ["*.rpm"],
  "npm": ["*.tgz"],
  "debian": ["*.deb"],
  "nuget": ["*.nupkg"],
  "bower": ["*.tar.gz"],
  "pypi": ["*.tar.gz"]
]

// If this Artifactory instance is part of an HA cluster and is the primary
// node, reload the user plugins on all the other nodes in the cluster. If this
// instance is not the primary, reload the plugins on the primary node instead,
// causing it to reload the rest of the cluster. This allows this plugin to be
// updated properly when necessary.
if (ctx.artifactoryHome.isHaConfigured()) {
  log.info("HA cluster detected")
  def commonService = ctx.beanForType(ArtifactoryServersCommonService)
  def servers
  // decide which servers to send the reload request to
  if (ctx.beanForType(AddonsManager).addonByType(HaAddon).isPrimary()) {
    log.info("Reloading plugins on all non-primary cluster nodes")
    servers = commonService.otherActiveMembers
  } else {
    log.info("Reloading plugins on the primary cluster node")
    servers = [commonService.runningHaPrimary]
  }
  // extract the Auth header out of the request so it can be passed along
  def req = RequestThreadLocal.context.get()?.requestThreadLocal
  def headerValue = req?.request?.getHeader('Authorization')
  if (headerValue && ctx.beanForType(UserGroupService).currentUser()) {
    // send the reload request to each specified server
    for (server in servers) {
      // build the url to send to
      def url = (server.contextUrl - ~'/$') + '/api/plugins/reload'
      // build the HTTP client object
      def clientConf = new HttpClientConfigurator()
      clientConf.soTimeout(60000).connectionTimeout(60000)
      def client = clientConf.hostFromUrl(url).retry(0, false).client
      // build the request and set the auth header
      def method = new HttpPost(url)
      method.addHeader('Authorization', headerValue)
      // send the request in a different thread
      threadPool.submit { client.execute(method) }
    }
  }
}

// After upgrading Artifactory to 4.11+, migrate the Xray configuration from the
// json file to the Artifactory core config.
def tryMigrateConfig() {
  // If Artifactory supports Xray, get the required Xray-related classes
  def xrayAddonClass = null, xrayConfigModelClass = null, xrayRepoClass = null
  try {
    def addonName = 'org.artifactory.addon.xray.XrayAddon'
    def modelName = 'org.artifactory.rest.common.model.xray.XrayConfigModel'
    def repoName = 'org.artifactory.addon.xray.XrayRepoModel'
    xrayAddonClass = Class.forName(addonName)
    xrayConfigModelClass = Class.forName(modelName)
    xrayRepoClass = Class.forName(repoName)
  } catch (ClassNotFoundException ex) {
    return
  }
  def addonsManager = ctx.beanForType(AddonsManager)
  def xrayAddon = addonsManager.addonByType(xrayAddonClass)
  def cfg = readConfigFile()
  // If the plugin's Xray configuration does not exist, exit
  if (!cfg.config) {
    def msg = "No Xray config exists to migrate."
    log.error("Error migrating Xray config: $msg")
    return
  }
  // If the Artifactory license does not support Xray, exit
  if (!addonsManager.xrayTrialSupported()) {
    def msg = "Artifactory license does not support Xray."
    log.error("Error migrating Xray config: $msg")
    return
  }
  // If the real Xray configuration already exists, exit
  if (xrayAddon.isXrayConfigExist()) {
    def msg = "Xray config already exists on this instance."
    log.error("Error migrating Xray config: $msg")
    return
  }
  // Migrate the Xray config
  log.info("Migrating Xray config.")
  def xrayConfigModel = xrayConfigModelClass.newInstance()
  xrayConfigModel.xrayBaseUrl = cfg.config?.xrayBaseUrl
  xrayConfigModel.xrayId = cfg.config?.xrayId
  xrayConfigModel.artifactoryId = cfg.config?.artifactoryId
  xrayConfigModel.xrayUser = cfg.config?.xrayUser
  xrayConfigModel.xrayPass = cfg.config?.xrayPass
  if (xrayConfigModel.validate(true)) {
    // Migrate the list of indexed repositories
    def localRepos = ctx.centralConfig.descriptor.localRepositoriesMap
    def remoteRepos = ctx.centralConfig.descriptor.remoteRepositoriesMap
    localRepos.each { k, v ->
      def obj = ["name": v.key, "pkgType": v.type.type, "type": "local"]
      v.xrayIndex = obj in cfg.index
    }
    remoteRepos.each { k, v ->
      def obj = ["name": v.key, "pkgType": v.type.type, "type": "remote"]
      v.xrayIndex = obj in cfg.index
    }
    // Would just call xrayAddon.createXrayConfig, but that function checks for
    // admin privs, which we don't have since we're the system user. So it has
    // to be done this way instead.
    def descriptor = ctx.centralConfig.mutableDescriptor
    descriptor.xrayConfig = xrayConfigModel.toDescriptor()
    descriptor.localRepositoriesMap = localRepos
    descriptor.remoteRepositoriesMap = remoteRepos
    ctx.centralConfig.setConfigXml(JaxbHelper.toXml(descriptor), true)
  } else {
    def msg = "Failed to create Xray config."
    log.error("Error migrating Xray config: $msg")
    return
  }
  // Migration is complete
  log.info("Successfully migrated the Xray configuration.")
  // Delete the old config file and this plugin
  log.info("Deleting compatibility files.")
  def etcdir = ctx.artifactoryHome.etcDir
  def groovyfile = new File(etcdir, "plugins/xrayCompatibility.groovy")
  def configfile = new File(etcdir, "plugins/xrayCompatibility.json")
  groovyfile.delete()
  configfile.delete()
  if (groovyfile.exists() || configfile.exists()) {
    def msg = "One or more compatibility files failed to delete."
    log.error("Error migrating Xray config: $msg")
  }
  // Reload plugins on this instance
  ctx.beanForType(AddonsManager).addonByType(PluginsAddon).reloadPlugins()
  // Migration is complete
  log.info("Successfully deleted compatibility files.")
}

executions {
  // Return the version of this plugin.
  xrayCompatibilityVersion(httpMethod: 'GET', users: ['anonymous']) { params ->
    status = 200
    message = version
  }

  // Given a list of repos, index all artifacts in all of those repos. This is
  // used in the onboarding stage.
  xrayIndex(httpMethod: 'POST') { params ->
    // Ensure the given repo list is not empty
    def repos = params?.get('repos')
    if (!repos) {
      log.debug("No repos were given to index.")
      status = 400
      message = "No repos were given to index."
      return
    }
    // Get the Xray uuid, so we can update the Xray properties later
    def uuid = readConfigFile().config?.xrayId
    if (!uuid) {
      log.debug("Xray UUID not found.")
      status = 400
      message = "Xray is not configured on this instance."
      return
    }
    def statprop = "xray.${uuid}.index.status"
    def realrepos = []
    // Iterate over all given repositories, and ensure that they are indexable
    for (reponame in repos) {
      def (repo, idxer) = getRealRepoAndIndexer(reponame)
      if (!repo || !isRepoIndexed(repo.key)) {
        log.warn("Repository $reponame does not exist or cannot be indexed.")
        status = 400
        message = "Repository $reponame does not exist or cannot be indexed."
        return
      }
      // If this repo is indexable, add it to the new list
      realrepos << [repo, idxer]
    }
    def aqlserv = ctx.beanForType(AqlService)
    // Iterate over all given repositories, to index all their artifacts
    for (realrepo in realrepos) {
      def (repo, idxer) = realrepo
      log.info("Indexing Xray archives in repository ${repo.key}.")
      def repokey = repo.key
      if (repo.type == 'remote') repokey += "-cache"
      // Build an AQL query to find all indexable artifacts in this repo
      def names = idxer.collect { ['name': ['$match': it]] }
      def query = ['repo': repokey, '$or': names]
      def aql = "items.find(${new JsonBuilder(query).toString()})"
      def results = aqlserv.executeQueryEager(aql).results
      // Iterate through the results of the AQL query
      for (result in results) {
        def path = "$result.path/$result.name"
        def rpath = RepoPathFactory.create(result.repo, path)
        def item = repositories.getFileInfo(rpath)
        if (repositories.hasProperty(item.repoPath, statprop)) continue
        // Enque an event to index this artifact in Xray
        enqueueEvent {
          xrayPropertiesDeploy(uuid, item.repoPath)
          xrayIndexEvent('created', item, null)
        }
      }
    }
    status = 202
  }

  // Clear all queued Xray events.
  xrayClearAllIndexTasks(httpMethod: 'DELETE') { params ->
    synchronized (eventMutex) {
      eventQueue.clear()
    }
    status = 200
    message = "Successfully triggered removal of all current Xray index tasks."
  }

  // Given a repo, count all artifacts in that repo that may be indexed, are
  // being indexed, and are already indexed, and send back those statistics.
  xrayIndexStats(httpMethod: 'GET') { params ->
    // Get the Xray uuid, so we can check the Xray properties later
    def uuid = readConfigFile().config?.xrayId
    if (!uuid) {
      log.debug("Xray UUID not found.")
      status = 400
      message = "Xray is not configured on this instance."
      return
    }
    // Ensure the given repo was actually provided
    def reponame = params?.get('repo')?.get(0)
    if (!reponame) {
      log.debug("Repository key cannot be empty.")
      status = 400
      message = "Repository key cannot be empty."
      return
    }
    // Ensure the given repo exists and can be indexed
    def (repo, idxer) = getRealRepoAndIndexer(reponame)
    if (!repo) {
      log.debug("Repository '$reponame' is not supported for Xray indexing.")
      status = 400
      message = "Repository '$reponame' is not supported for Xray indexing."
      return
    }
    def aqlserv = ctx.beanForType(AqlService)
    def repokey = repo.key
    if (repo.type == 'remote') repokey += "-cache"
    // Build an AQL query to count the indexable artifacts in this repo
    def names = idxer.collect { ['name': ['$match': it]] }
    def query = ['repo': repokey, '$or': names]
    def aql = "items.find(${new JsonBuilder(query).toString()})"
    def all = aqlserv.executeQueryEager(aql).size
    // Build an AQL query to count the indexed artifacts in this repo
    query["@xray.${uuid}.index.status"] = "Indexed"
    aql = "items.find(${new JsonBuilder(query).toString()})"
    def indexed = aqlserv.executeQueryEager(aql).size
    // Build an AQL query to count the indexing artifacts in this repo
    query["@xray.${uuid}.index.status"] = "Indexing"
    aql = "items.find(${new JsonBuilder(query).toString()})"
    def indexing = aqlserv.executeQueryEager(aql).size
    // Put the results into a JSON object and return it
    def result = ["started": indexing, "completed": indexed, "potential": all]
    status = 200
    message = new JsonBuilder(result).toString()
  }

  // Send back a list of all local and remote repos that are not configured for
  // indexing.
  xrayNonIndexRepos(httpMethod: 'GET') { params ->
    status = 200
    message = new JsonBuilder(getXrayIndexLists()[1]).toString()
  }

  // Send back a list of all local and remote repos that are configured for
  // indexing.
  xrayIndexReposGet(httpMethod: 'GET') { params ->
    status = 200
    message = new JsonBuilder(getXrayIndexLists()[0]).toString()
  }

  // Given a list of local and remote repos, configure those repos for indexing.
  xrayIndexReposPut(httpMethod: 'PUT') { params, ResourceStreamHandle body ->
    log.info("Adding repos to Xray index.")
    def json = null
    // Get the repository list from the request body
    try {
      json = new JsonSlurper().parse(body.inputStream)
      if (!json || !(json instanceof List)) throw new JsonException()
    } catch (JsonException ex) {
      status = 400
      message = "Failed to update Xray config."
    }
    log.debug("Requested repos: $json")
    def indexed = []
    // Iterate over each given repository
    for (rep in json) {
      def repo = repositories.getRepositoryConfiguration(rep?.name)
      // Ensure that the repo exists
      if (!repo || repo.type != rep?.type) {
        status = 400
        message = "Request could not be completed."
        message += " Repository key: '$rep.name' does not exist."
        return
      }
      // Ensure that the repo is local, or remote with a cache
      if (!(repo.type in ['local', 'remote']) ||
          (repo.type == 'remote' && !repo.isStoreArtifactsLocally())) {
        status = 400
        message = "Request could not be completed. Repository key: '$rep.name'"
        message += " is not supported for Xray indexing."
        return
      }
      // Add the repo to the new list of configured repos
      def obj = ["name": repo.key]
      obj["pkgType"] = repo.packageType
      obj["type"] = repo.type
      indexed << obj
    }
    // Set the new repo list in the config file
    if (!updateConfigFile { it.index = indexed }) {
      status = 500
      message = "Failed to write Xray config file."
      return
    }
    status = 200
  }

  // Given an Xray configuration, replace the current configuration with the new
  // one.
  xrayPut(httpMethod: 'PUT') { params, ResourceStreamHandle body ->
    log.info("Updating Xray config.")
    try {
      // Parse the request body as JSON and validate it
      def json = new JsonSlurper().parse(body.inputStream)
      validateXrayConfig(json)
      // Write the new configuration to the config file
      if (!updateConfigFile { it.config = json }) {
        status = 500
        message = "Failed to write Xray config file."
        return
      }
    } catch (JsonException ex) {
      status = 400
      message = "Failed to create Xray config."
      return
    }
    status = 200
  }

  // Delete the Xray configuration and the Xray user.
  xrayDelete(httpMethod: 'DELETE') { params ->
    log.info("Deleting Xray config.")
    // Erase the configuration in the config file
    log.debug("Removing Xray config.")
    if (!updateConfigFile { it.config = null }) {
      status = 500
      message = "Failed to write Xray config file."
      return
    }
    // Delete the Xray user
    log.debug("Deleting Xray user 'xray'.")
    ctx.securityService.deleteUser('xray')
    status = 200
  }

  // Given an Xray configuration, set it as the new configuration and create an
  // Xray user. Send back login information for this new user.
  xrayPost(httpMethod: 'POST') { params, ResourceStreamHandle body ->
    log.info("Creating Xray config.")
    // If a configuration already exists, don't replace it: The PUT endpoint
    // should be used for that instead
    if (readConfigFile().config) {
      status = 409
      message = "Xray config already exists on this instance."
      return
    }
    try {
      // Parse the request body as JSON and validate it
      def json = new JsonSlurper().parse(body.inputStream)
      validateXrayConfig(json)
      // Write the new configuration to the config file
      if (!updateConfigFile { it.config = json }) {
        status = 500
        message = "Failed to write Xray config file."
        return
      }
    } catch (JsonException ex) {
      log.warn("Failed to create Xray config.")
      status = 400
      message = "Failed to create Xray config."
      return
    }
    // Try to create the Xray user
    log.debug("Creating Xray user 'xray'.")
    def pass = null
    def sec = ctx.securityService
    def xrayuser = null
    // Find the Xray user, if it already exists
    try {
      xrayuser = sec.findUser('xray')
    } catch (UsernameNotFoundException ex) {
      xrayuser = null
    }
    if (!xrayuser) {
      // If the Xray user doesn't exist, make one
      def pwid = UUID.randomUUID().toString()
      def pw = sec.generateSaltedPassword(pwid)
      xrayuser = new UserInfoBuilder('xray').password(pw).admin(true).build()
      sec.createUser(xrayuser)
      pass = sec.createEncryptedPasswordIfNeeded(xrayuser, pwid)
    } else {
      log.debug("Xray user 'xray' already exists.")
      // If the Xray user does exist, retrieve its encrypted password
      pass = CryptoHelper.encryptIfNeeded(xrayuser.password)
    }
    // Send back the username and password for Xray to use
    status = 201
    message = new JsonBuilder(["artUser": "xray", "artPass": pass]).toString()
  }
}

storage {
  // If a new artifact is about to overwrite an old artifact, and the old
  // artifact is indexed by Xray, send a DELETE event to Xray.
  beforeCreate { item ->
    // If the artifact doesn't exist or isn't indexed, don't bother
    if (!repositories.exists(item.repoPath)) return
    if (item.isFolder() || !isRepoIndexed(item.repoKey)) return
    if (!canItemIndex(item)) return
    // Get the FileInfo object for the item, and enqueue an event for it
    enqueueEvent { xrayIndexEvent('deleted', item, null) }
  }

  // If a new artifact has been deployed, and it should be indexed, send a
  // CREATE event to Xray.
  afterCreate { item ->
    // If the artifact isn't indexed, don't bother
    if (item.isFolder() || !isRepoIndexed(item.repoKey)) return
    if (!canItemIndex(item)) return
    // Get the Xray uuid, so we can update the Xray properties later
    def uuid = readConfigFile().config?.xrayId
    if (!uuid) {
      log.debug("Xray UUID not found.")
      return
    }
    // If the item is already indexed, don't bother
    def statprop = "xray.${uuid}.index.status"
    if (repositories.hasProperty(item.repoPath, statprop)) return
    // Get the FileInfo object for the item, and enqueue an event for it
    enqueueEvent {
      xrayPropertiesDeploy(uuid, item.repoPath)
      xrayIndexEvent('created', item, null)
    }
  }

  // If an artifact is moved, send an appropriate event to Xray if necessary.
  afterMove { item, targetRepoPath, properties ->
    // If the artifact isn't indexed, don't bother
    if (item.isFolder()) return
    // Get the Xray uuid, so we can update the Xray properties later
    def uuid = readConfigFile().config?.xrayId
    if (!uuid) {
      log.debug("Xray UUID not found.")
      return
    }
    // Get the source and target indexing data
    def statprop = "xray.${uuid}.index.status"
    def hasprop = repositories.hasProperty(item.repoPath, statprop)
    def targ = repositories.getFileInfo(targetRepoPath)
    def srcidx = isRepoIndexed(item.repoKey) && hasprop
    def trgidx = isRepoIndexed(targ.repoKey)
    if (srcidx && trgidx) {
      // If both the source and target are indexed, clear the properties on the
      // target and send a MOVE event
      xrayPropertiesClear(uuid, targ.repoPath)
      if (!canItemIndex(targ)) return
      enqueueEvent { xrayIndexEvent('moved', targ, item.repoPath) }
    } else if (srcidx && !trgidx) {
      // If the source is indexed but the target is not, clear the properties on
      // the target and send a DELETE event for the source
      xrayPropertiesClear(uuid, targ.repoPath)
      if (!canItemIndex(item)) return
      enqueueEvent { xrayIndexEvent('deleted', item, null) }
    } else if (!srcidx && trgidx) {
      // If the target is indexed but the source is not, send a CREATE event
      if (!canItemIndex(targ)) return
      if (repositories.hasProperty(targ.repoPath, statprop)) return
      enqueueEvent {
        xrayPropertiesDeploy(uuid, targ.repoPath)
        xrayIndexEvent('created', targ, null)
      }
    }
  }

  // If an artifact is copied, send an appropriate event to Xray if necessary.
  afterCopy { item, targetRepoPath, properties ->
    // If the artifact isn't indexed, don't bother
    if (item.isFolder()) return
    // Get the Xray uuid, so we can update the Xray properties later
    def uuid = readConfigFile().config?.xrayId
    if (!uuid) {
      log.debug("Xray UUID not found.")
      return
    }
    // Get the source and target indexing data
    def statprop = "xray.${uuid}.index.status"
    def hasprop = repositories.hasProperty(item.repoPath, statprop)
    def targ = repositories.getFileInfo(targetRepoPath)
    def srcidx = isRepoIndexed(item.repoKey) && hasprop
    // If the source has properties, clear them from the target
    if (srcidx) xrayPropertiesClear(uuid, targ.repoPath)
    // If the target is not indexed, don't bother
    if (!isRepoIndexed(targ.repoKey)) return
    if (!canItemIndex(targ)) return
    if (srcidx) {
      // If the source and target are indexed, send a COPY event
      enqueueEvent { xrayIndexEvent('copied', targ, item.repoPath) }
    } else {
      // If the target is indexed but the source is not, send a CREATE event
      if (repositories.hasProperty(targ.repoPath, statprop)) return
      enqueueEvent {
        xrayPropertiesDeploy(uuid, targ.repoPath)
        xrayIndexEvent('created', targ, null)
      }
    }
  }

  // If an indexed artifact is deleted, send a DELETE event to Xray.
  afterDelete { item ->
    // If the artifact isn't indexed, don't bother
    if (item.isFolder() || !isRepoIndexed(item.repoKey)) return
    if (!canItemIndex(item)) return
    // If the delete is part of a non-delete operation, don't bother
    def transname = TransactionSynchronizationManager.currentTransactionName
    if (!transname.toLowerCase().contains('undeploy')) return
    // Get the FileInfo object for the item, and enqueue an event for it
    enqueueEvent { xrayIndexEvent('deleted', item, null) }
  }
}

build {
  // If a build is deployed, send a BUILD event to Xray.
  afterSave { buildRun ->
    // Get the Artifactory ID
    def artifId = readConfigFile().config?.artifactoryId
    if (!artifId) {
      log.debug("Xray Artifactory ID not found.")
      return
    }
    def buildName = buildRun.name
    def buildNumber = buildRun.number
    enqueueEvent {
      // Create the request body
      def body = ["eventType": "build"]
      body["artifactoryId"] = artifId
      body["repoKey"] = "build"
      body["path"] = "${buildName}_build-info.json"
      body["buildName"] = buildName
      body["buildNumber"] = buildNumber
      // Send the request and retrieve the response
      def resp = sendRequest('POST', 'index', new JsonBuilder(body).toString())
      def stat = resp?.statusLine?.statusCode
      if (!stat || stat < 200 || stat >= 300) {
        log.error("Error communicating with Xray: $stat")
      }
    }
  }
}

// Given a closure, add the closure to the event queue so that it can be run in
// a separate thread.
def enqueueEvent(func) {
  // Add the event to the queue, and if the queue is currently being processed,
  // return. If the queue is not being processed, start processing it.
  synchronized (eventMutex) {
    eventQueue.add(func)
    if (eventProcessing) return
    else eventProcessing = true
  }
  // Create a thread action to process the queue
  threadPool.submit {
    // Loop until the queue is empty, then stop processing it and exit
    while (true) {
      // Pull the next event out of the queue
      def event = null
      synchronized (eventMutex) {
        if (eventQueue.isEmpty()) {
          eventProcessing = false
          return
        }
        event = eventQueue.poll()
      }
      // Execute the next event
      try {
        event()
      } catch (Exception ex) {
        log.error("Error executing Xray event.", ex)
      }
    }
  }
}

// Given an artifact, set the indexing status of that artifact to 'Indexing',
// and set the lastUpdated time to the current time. This should be done when
// indexing starts on that artifact.
def xrayPropertiesDeploy(uuid, repoPath) {
  xrayPropertiesClear(uuid, repoPath)
  def time = "${System.currentTimeMillis()}"
  repositories.setProperty(repoPath, "xray.${uuid}.index.lastUpdated", time)
  repositories.setProperty(repoPath, "xray.${uuid}.index.status", "Indexing")
}

// Given an artifact, delete all Xray-related properties from that artifact.
// This should be done when an artifact is no longer indexed.
def xrayPropertiesClear(uuid, repoPath) {
  repositories.deleteProperty(repoPath, "xray.${uuid}.index.status")
  repositories.deleteProperty(repoPath, "xray.${uuid}.index.lastUpdated")
  repositories.deleteProperty(repoPath, "xray.${uuid}.alert.topSeverity")
  repositories.deleteProperty(repoPath, "xray.${uuid}.alert.lastUpdated")
  repositories.deleteProperty(repoPath, "xray.${uuid}.alert.component")
}

// Send an event to Xray. 'event' is a string "created", "deleted", "moved",
// "copied", etc. 'item' is the FileInfo object of the artifact in question.
// 'repoPath2' is the source RepoPath of the artifact for "moved" and "copied"
// events, and should be null otherwise.
def xrayIndexEvent(event, item, repoPath2) {
  // Get the Artifactory ID
  def artifId = readConfigFile().config?.artifactoryId
  if (!artifId) return false
  // Get the checksums info for the artifact
  def checksums = [:]
  checksums["md5"] = item.checksumsInfo.md5
  checksums["sha1"] = item.checksumsInfo.sha1
  // Create the request body
  def body = ["eventType": event]
  body["artifactoryId"] = artifId
  body["checksums"] = checksums
  body["repoKey"] = item.repoPath.repoKey
  body["path"] = '/' + item.repoPath.path
  // If this is a CREATE event for a Debian repo, try adding a componentId
  def pType = repositories.getRepositoryConfiguration(item.repoKey).packageType
  if (pType == 'debian' && event == 'created') {
    def dist = repositories.getProperty(item.repoPath, 'deb.distribution')
    def arch = repositories.getProperty(item.repoPath, 'deb.architecture')
    def name = repositories.getProperty(item.repoPath, 'deb.name')
    def vers = repositories.getProperty(item.repoPath, 'deb.version')
    if (dist && arch && name && vers) {
      body["componentId"] = "${dist}.${arch}.${name}.${vers}"
    }
  }
  // Add the source data to the request body if available
  if (repoPath2) {
    body["sourceRepoKey"] = repoPath2.repoKey
    body["sourcePath"] = '/' + repoPath2.path
  }
  // Send the request and retrieve the response
  def resp = sendRequest('POST', 'index', new JsonBuilder(body).toString())
  def stat = resp?.statusLine?.statusCode
  if (!stat || stat < 200 || stat >= 300) {
    log.error("Error communicating with Xray: $stat")
  }
  return stat && stat >= 200 && stat < 300
}

// Return the cached config file from the 'configData' global. If the
// configuration has not yet been cached, read it from the config file first. If
// the file is missing or invalid, use the default clean config instead.
def readConfigFile() {
  synchronized (configMutex) {
    if (!configData) {
      def defaultcfg = ['config': null, 'index': []]
      def etcdir = ctx.artifactoryHome.etcDir
      def cfgfile = new File(etcdir, "plugins/xrayCompatibility.json")
      try {
        configData = new JsonSlurper().parse(cfgfile)
        log.debug("Config file loaded successfully from disk.")
      } catch (IOException ex) {
        configData = defaultcfg
        log.debug("Unable to load config file from disk, using default.")
      } catch (JsonException ex) {
        configData = defaultcfg
        log.debug("Unable to load config file from disk, using default.")
      }
    }
    return configData
  }
}

// Given a closure, run the closure, passing it the current Xray configuration
// object. The closure should mutate the object and return. Then, write the
// updated config object back to the config file.
def updateConfigFile(callback) {
  synchronized (configMutex) {
    def cfg = readConfigFile()
    // bugfix: Ensure that cfg is at least partially evaluated, by running its
    // equals() method. If cfg is not at least partially evaluated, clone() will
    // return null.
    if (cfg.equals(null)) return false
    def json = cfg.clone()
    callback(json)
    if (cfg == json) return true
    def etcdir = ctx.artifactoryHome.etcDir
    def cfgfile = new File(etcdir, "plugins/xrayCompatibility.json")
    try {
      cfgfile.text = new JsonBuilder(json).toString()
      configData = json
      log.debug("Config file saved successfully to disk.")
      return true
    } catch (IOException ex) {
      log.debug("Unable to save config file to disk.")
      return false
    } catch (JsonException ex) {
      log.debug("Unable to save config file to disk.")
      return false
    }
  }
}

// Given an HTTP method, an api path, and optionally a request body, send an
// HTTP request to Xray's api. Return the response.
def sendRequest(method, path, body) {
  // Get the Xray username, password, and URL from the config
  def cfg = readConfigFile()
  def user = cfg.config?.xrayUser
  def pasw = cfg.config?.xrayPass
  def baseurl = cfg.config?.xrayBaseUrl
  if (!user || !pasw || !baseurl) return null
  // Build the auth header and the destination URL
  def auth = "Basic ${"$user:$pasw".bytes.encodeBase64()}"
  def url = (baseurl - ~'/$') + '/api/v1/' + (path - ~'^/')
  // Build the request object (the default should never happen)
  def req = null
  switch (method) {
  case 'PUT': req = new HttpPut(url); break
  case 'GET': req = new HttpGet(url); break
  case 'POST': req = new HttpPost(url); break
  case 'DELETE': req = new HttpDelete(url); break
  default: throw new RuntimeException("Invalid method $method.")
  }
  // Add the User-Agent, Content-Type, and Authorization headers
  req.addHeader("User-Agent", HttpUtils.artifactoryUserAgent)
  req.addHeader("Content-Type", "application/json")
  req.addHeader("Authorization", auth)
  // Add the body, if available
  if (body) req.entity = new StringEntity(body)
  // Make the request and return the response object
  return getHttpClient().execute(req)
}

// Return the HTTP client object. Initialize it first, if it hasn't been
// initialized yet.
def getHttpClient() {
  if (!httpclient) {
    def builder = HttpClients.custom()
    builder.maxConnTotal = 50
    builder.maxConnPerRoute = 25
    httpclient = builder.build()
  }
  return httpclient
}

// Given a new Xray config, validate it to ensure that all the required fields
// exist. If the password field isn't there, try using the existing password
// from the old config.
def validateXrayConfig(json) {
  if (!json?.xrayPass) json.xrayPass = readConfigFile().config?.xrayPass
  def invalid = !json || !json.xrayBaseUrl
  invalid = invalid || !json.xrayId || !json.artifactoryId
  invalid = invalid || !json.xrayUser || !json.xrayPass
  if (invalid) {
    log.debug("Invalid Xray config model received.")
    throw new JsonException()
  }
}

// Check if the given repository is configured for indexing by Xray.
def isRepoIndexed(repokey) {
  def repo = getRealRepoAndIndexer(repokey)?.getAt(0)
  def obj = ["name": repo?.key]
  obj["pkgType"] = repo?.packageType
  obj["type"] = repo?.type
  return obj in readConfigFile().index
}

// Check if the given artifact can be indexed by Xray.
def canItemIndex(item) {
  def (repo, idxer) = getRealRepoAndIndexer(item.repoKey)
  if (!repo) return false
  for (matcher in idxer) {
    if (matcher[0] == '*') {
      if (item.name.endsWith(matcher[1..-1])) return true
    } else {
      if (item.name == matcher) return true
    }
  }
  return false
}

// Given a repository, return its RepositoryConfiguration object, as well as its
// list of indexable file patterns. If the repository is not indexable, return
// null for both values.
def getRealRepoAndIndexer(repokey) {
  def repo = repositories.getRepositoryConfiguration(repokey - ~'-cache$')
  if (!repo) repo = repositories.getRepositoryConfiguration(repokey)
  def idxer = xrayIndexers[repo?.packageType]
  if (!idxer || repo?.type != 'local' && repo?.type != 'remote') return []
  if (repo?.type == 'remote' && !repo?.isStoreArtifactsLocally()) return []
  return [repo, idxer]
}

// Return two lists: one of all repositories that are configured for indexing,
// and another of all other local and remote repositories.
def getXrayIndexLists() {
  log.debug("Retrieving Xray index-enabled repos.")
  def repos = repositories.localRepositories + repositories.remoteRepositories
  def indexed = [], nonindexed = []
  for (repokey in repos) {
    def repo = repositories.getRepositoryConfiguration(repokey)
    def obj = ["name": repo.key]
    obj["pkgType"] = repo.packageType
    obj["type"] = repo.type
    if (isRepoIndexed(repokey)) indexed << obj
    else nonindexed << obj
  }
  if (!updateConfigFile { it.index = indexed }) {
    log.error("Unable to write Xray config file.")
  }
  return [indexed, nonindexed]
}

// Attempt to migrate the config, in case Artifactory was upgraded
threadPool.submit { tryMigrateConfig() }
