/*
 * Copyright (C) 2017 JFrog Ltd.
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
import groovy.json.JsonOutput;
import groovy.json.JsonSlurper
import groovy.xml.XmlUtil
import org.artifactory.addon.replication.AllReplicationRequestHandler
import org.artifactory.descriptor.replication.LocalReplicationDescriptor
import org.artifactory.repo.RepoPathFactory
import groovy.transform.Field
import javax.xml.bind.JAXBContext
import org.artifactory.descriptor.config.CentralConfigDescriptorImpl
import org.artifactory.repo.service.InternalRepositoryService
import org.artifactory.repo.LocalRepositoryConfigurationImpl
import org.artifactory.repo.HttpRepositoryConfigurationImpl
import org.artifactory.repo.VirtualRepositoryConfigurationImpl
import org.artifactory.descriptor.repo.RepoType
import org.codehaus.jackson.map.ObjectMapper

@Field def configFileLocation = 'plugins/artifactoryMigrationHelper.json'
@Field def config = loadConfig()

def loadConfig() {
    def etcdir = ctx.artifactoryHome.etcDir
    def propsfile = new File(etcdir, configFileLocation)
    def props = new JsonSlurper().parse(propsfile)
    def target = props.target
    if (!target.endsWith('/')) target += '/'
    return [
        target: target, username: props.username, password: props.password,
        replicationTimes: props.replicationTimes,
        replicationStep: props.replicationStep,
        replicationInitialHour: props.replicationInitialHour,
        replicationFinalHour: props.replicationFinalHour
    ]
}

def getCron() {
    def etcdir = ctx.artifactoryHome.etcDir
    def propsfile = new File(etcdir, configFileLocation)
    def props = new JsonSlurper().parse(propsfile)
    return props.cron
}

jobs {
    artifactoryMigrationSetupJob(cron: getCron()) {
        try {
            setupArtifactoryMigration()
        } catch (Exception e) {
            log.error("Failed to setup Artifactory migration", e)
        }
    }
}

executions {
    artifactoryMigrationSetup() {
        try {
            setupArtifactoryMigration()
            message = 'Setup completed successfully'
            status = 200
        } catch (Exception e) {
            log.error("Failed to setup Artifactory migration", e)
            message = "Failed to setup Artifactory migration: ${e.getMessage()}"
            status = 500
        }
    }
}

def setupArtifactoryMigration() {
    log.info "Setting up replication to $config.target"
    validateConfigurations()
    def changesHolder = []
    def localConfig = getLocalArtifactoryConfig()
    def remoteRepositories = getRemoteArtifactoryRepositories()
    log.debug "Remote repositories: $remoteRepositories"
    handleLocalRepositories(localConfig, remoteRepositories, changesHolder)
    handleRemoteRepositories(localConfig, remoteRepositories, changesHolder)
    handleVirtualRepositories(localConfig, remoteRepositories, changesHolder)
    if (hasChanges(changesHolder)) {
        log.info "Detected changes on $changesHolder"
    } else {
        log.info "No changes detected"
    }
    handleReplication(localConfig)
}

def validateConfigurations() {
    if (config.replicationInitialHour < 0 || config.replicationInitialHour > 23) {
        throw new Exception("Config parameter replicationInitialHour must be within range 0 to 23. Current value: $config.replicationInitialHour")
    }

    if (config.replicationFinalHour < 1 || config.replicationFinalHour > 24) {
        throw new Exception("Config parameter replicationFinalHour must be within range 1 to 24. Current value: $config.replicationFinalHour")
    }

    if (config.replicationFinalHour <= config.replicationInitialHour) {
        throw new Exception("Config parameter replicationFinalHour must be bigger than replicationInitialHour. Current values: $config.replicationInitialHour - $config.replicationFinalHour")
    }

    if (config.replicationTimes <= 0) {
        throw new Exception("Config parameter replicationTimes must be bigger than 0. Current value: $config.replicationTimes")
    }

    if (config.replicationStep <= 0) {
        throw new Exception("Config parameter replicationStep must be bigger than 0. Current value: $config.replicationStep")
    }
}

def hasChanges(changesHolder) {
    log.debug "changesHolder: $changesHolder"
    return !changesHolder.isEmpty()
}

def handleLocalRepositories(localConfig, remoteRepositories, changesHolder) {
    log.info "Handling local repositories"
    def existingRepositories = remoteRepositories
        .findAll { it.type == 'LOCAL' }
        .collect { it.key }
    log.debug "Existing local repositories: $existingRepositories"
    def missingRepositories = localConfig.localRepositories.localRepository.findAll {
        !(it.key in existingRepositories)
    }
    if (!missingRepositories.isEmpty()) { changesHolder.push('Local Repositories') }
    missingRepositories.each {
        copyLocalRepositoryToRemoteArtifactory(it.key.toString())
    }
}

def handleRemoteRepositories(localConfig, remoteRepositories, changesHolder) {
    log.info "Handling remote repositories"
    def existingRepositories = remoteRepositories
        .findAll { it.type == 'REMOTE' }
        .collect { it.key }
    log.debug "Existing remote repositories: $existingRepositories"
    def missingRepositories = localConfig.remoteRepositories.remoteRepository.findAll {
        !(it.key in existingRepositories)
    }
    if (!missingRepositories.isEmpty()) { changesHolder.push('Remote Repositories') }
    missingRepositories.each {
        copyRemoteRepositoryToRemoteArtifactory(it.key.toString())
    }
}

def handleVirtualRepositories(localConfig, remoteRepositories, changesHolder) {
    log.info "Handling virtual repositories"
    def existingRepositories = remoteRepositories
        .findAll { it.type == 'VIRTUAL' }
        .collect { it.key }
    log.debug "Existing virtual repositories: $existingRepositories"
    def missingRepositories = localConfig.virtualRepositories.virtualRepository.findAll {
        !(it.key in existingRepositories)
    }
    if (!missingRepositories.isEmpty()) { changesHolder.push('Virtual Repositories') }
    missingRepositories.each {
        copyVirtualRepositoryToRemoteArtifactory(it.key.toString())
    }
}

def handleReplication(localConfig) {
    log.info "Handling replication configuration"
    def replicationCronExpOptions = getReplicationCronExpOptions()
    localConfig.localRepositories.localRepository.each {
        setupReplication(it.key.toString(), replicationCronExpOptions)
    }
}

def setupReplication(repoKey, replicationCronExpOptions) {
    if (isReplicationConfigurationPending(repoKey)) {
        // Select a random replication cron exp from the options
        def selectedCronExp = getRandomArrayValue(replicationCronExpOptions)
        addReplicationConfiguration(repoKey, selectedCronExp)
    }
}

def isReplicationConfigurationPending(repoKey) {
    def url = config.target + repoKey
    log.debug "Checking replication configuration for $repoKey -> $url"
    def descriptor = ctx.centralConfig.descriptor
    def replication = descriptor.getLocalReplication(repoKey, url)
    return replication == null
}

def addReplicationConfiguration(repoKey, cronExp) {
    log.info "Setting up replication to repo $repoKey with cron expression $cronExp"
    def replication = new LocalReplicationDescriptor()
    replication.enabled = true
    replication.cronExp = cronExp
    replication.syncDeletes = false
    replication.syncProperties = true
    replication.repoKey = repoKey
    replication.url = config.target + repoKey
    replication.username = config.username
    replication.password = config.password
    replication.enableEventReplication = true
    def descriptor = ctx.centralConfig.mutableDescriptor
    descriptor.addLocalReplication(replication)
    ctx.centralConfig.saveEditedDescriptorAndReload(descriptor)
}

/*
* Creates an array with all cron expression options according to the
* user preferences on the configuration file
*/
def getReplicationCronExpOptions() {
    def times = config.replicationTimes
    def step = config.replicationStep
    def initialHour = config.replicationInitialHour
    def finalHour = config.replicationFinalHour

    def hourStep = (finalHour - initialHour) / times as int
    def finalFirstExecutionHour = initialHour + hourStep as int
    def hour = initialHour
    def minute = 0

    def cronExpOptions = []
    while (hour < finalFirstExecutionHour) {
        def hourExpression = "$hour"
        for (i = 1; i < times; i++) {
            hourExpression = "$hourExpression,${hour + (hourStep * i)}"
        }
        cronExpOptions.push("0 $minute $hourExpression * * ?")

        minute += step
        while (minute >= 60) {
            hour++
            minute -= 60
        }
    }

    log.debug "Replication Cron Options: $cronExpOptions"
    return cronExpOptions
}

def getLocalArtifactoryConfig() {
    def artifactoryConfig = ctx.centralConfig.descriptor
    JAXBContext jc = JAXBContext.newInstance(CentralConfigDescriptorImpl.class)
    StringWriter sw = new StringWriter()
    jc.createMarshaller().marshal(artifactoryConfig, sw)
    return new XmlSlurper(false, false).parseText(sw.toString())
}

def getRemoteArtifactoryRepositories() {
    def url = config.target + 'api/repositories'
    def auth = "Basic ${"$config.username:$config.password".bytes.encodeBase64()}"
    def conn = null
    try {
        conn = new URL(url).openConnection()
        conn.requestMethod = 'GET'
        conn.setRequestProperty('Authorization', auth)
        def responseCode = conn.responseCode
        if (responseCode == 200) {
            return new JsonSlurper().parse(new InputStreamReader(conn.inputStream))
        } else {
            throw new Exception("Failed to get remote artifactory repositories. HTTP status: $responseCode")
        }
    } finally {
        conn?.disconnect()
    }
}

def copyLocalRepositoryToRemoteArtifactory(repoKey) {
    def repositoryService = ctx.beanForType(InternalRepositoryService.class)
    def repoDescriptor = repositoryService.localRepoDescriptorByKey(repoKey)
    def repoConfiguration = new LocalRepositoryConfigurationImpl(repoDescriptor)
    createRemoteArtifactoryRepo(repoKey, repoConfiguration)
}

def copyRemoteRepositoryToRemoteArtifactory(repoKey) {
    def repositoryService = ctx.beanForType(InternalRepositoryService.class)
    def repoDescriptor = repositoryService.remoteRepoDescriptorByKey(repoKey)
    def repoConfiguration = new HttpRepositoryConfigurationImpl(repoDescriptor)
    createRemoteArtifactoryRepo(repoKey, repoConfiguration)
}

def copyVirtualRepositoryToRemoteArtifactory(repoKey) {
    def repositoryService = ctx.beanForType(InternalRepositoryService.class)
    def repoDescriptor = repositoryService.virtualRepoDescriptorByKey(repoKey)
    def repoConfiguration = new VirtualRepositoryConfigurationImpl(repoDescriptor)
    createRemoteArtifactoryRepo(repoKey, repoConfiguration)
}

def createRemoteArtifactoryRepo(repoKey, repoConfiguration) {
    log.info "Creating repo $repoKey"
    def repoConfigurationJson = new ObjectMapper().writeValueAsString(repoConfiguration)
    log.debug "Repo Configuration: $repoConfigurationJson"
    repoConfigurationJson = removeNullProperties(repoConfigurationJson)
    log.debug "Cleaned Repo Configuration: $repoConfigurationJson"

    def url = config.target + "api/repositories/$repoKey"
    def auth = "Basic ${"$config.username:$config.password".bytes.encodeBase64()}"
    def conn = null
    try {
        conn = new URL(url).openConnection()
        conn.doOutput = true
        conn.requestMethod = 'PUT'
        conn.setRequestProperty('Authorization', auth)
        conn.setRequestProperty('Content-Type', 'application/json')
        conn.outputStream.write(repoConfigurationJson.bytes)
        def responseCode = conn.responseCode
        if (responseCode != 200) {
            log.error "Failed to create repository. Message: ${conn.errorStream.text}"
        }
    } finally {
        conn?.disconnect()
    }
}

def removeNullProperties(json) {
    def slurper = new JsonSlurper().parseText(json)
    for (Iterator<Map.Entry> it = slurper.entrySet().iterator(); it.hasNext();) {
        Map.Entry entry = it.next();
        if (entry.getValue() == null) {
            it.remove()
        }
    }
    return new JsonBuilder(slurper).toPrettyString()
}

def getRandomArrayValue(array) {
    def position = Math.random() * array.size() as int
    return array[position]
}
