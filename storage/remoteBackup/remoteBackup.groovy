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

import groovy.json.JsonException
import groovy.transform.Field
import groovy.json.JsonSlurper
import groovy.json.JsonBuilder

import org.artifactory.repo.RepoPathFactory

@Field final String REMOTE_BACKUP = "plugins/remoteBackup.json"

// A cron expression describing how often to run the backup. Modify this
// variable to customize the timing.
cronExpression = "0 0 0/1 * * ?"

jobs {
  remoteBackupJob(cron: cronExpression) {
    runBackup(null)
  }
}

executions {
  remoteBackup() { params ->
    def repos = params?.getAt('repos') ?: null
    def (complete, total) = runBackup(repos)
    if (complete instanceof CharSequence) {
      message = "Error copying items: $complete"
      status = 400
    } else if (complete < total) {
      message = "Successfully copied $complete of $total items"
    } else {
      message = "Successfully copied all $total items"
    }
  }
}

storage {
  afterCreate { item ->
    def etcdir = ctx.artifactoryHome.etcDir
    def cfgfile = new File(etcdir, REMOTE_BACKUP)
    def configExists = isRemoteBackupConfigExists(cfgfile.toString())
    if (!configExists) {
      return [complete="remoteBackup.json doesn't exists in plugins directory", total=0]
    }
    def cfg = getRemoteBackupConfigJSON(cfgfile.toString())
    if (cfg == null) {
      return [complete="Failed to read remoteBackup.json", total=0]
    }
    if (cfg == null) {
      return [complete="Failed to read remoteBackup.json", total=0]
    }
    if (item.repoKey in cfg && !item.isFolder()) {
      asSystem {
        def dest = cfg[item.repoKey]
        try {
          def destpath = RepoPathFactory.create(dest, item.relPath)
          def isCopyAllowed = canCopyBasedOnPackageType(item, repositories)
          if (isCopyAllowed) {
            repositories.copy(item.repoPath, destpath)
          } else {
            log.warn("Skipping copying $item.repoPath to $dest as it is not allowed because it is local generated path")
          }
        } catch (Exception ex) {
          log.warn("Unable to backup $item.repoPath to $dest: $ex.message")
        }
      }
    }
  }
}

static def canCopyBasedOnPackageType(item, repositories) {
  def packageType = repositories.getRepositoryConfiguration(item.getRepoKey()).getPackageType()
  def path = item.repoPath.getPath()
  switch (packageType) {
    case "npm":
      return !path.startsWith(".npm")
    case "rpm":
      return !path.startsWith("repodata")
    case "deb":
      return !path.startsWith("dists")
    default:
      return true
  }
}

def runBackup(repos) {
  def etcdir = ctx.artifactoryHome.etcDir
  def cfgfile = new File(etcdir, REMOTE_BACKUP)
  def configExists = isRemoteBackupConfigExists(cfgfile.toString())
  if (!configExists) {
    return [complete="remoteBackup.json doesn't exists in plugins directory", total=0]
  }
  def cfg = getRemoteBackupConfigJSON(cfgfile.toString())
  if (cfg == null) {
    return [complete="Failed to parse remoteBackup.json", total=0]
  }
  if (repos) {
    def cfgtmp = [:]
    for (repo in repos) {
      if (repo in cfg) cfgtmp[repo] = cfg[repo]
      else return ["Repo $repo not in configuration"]
    }
    cfg = cfgtmp
  }
  def complete = 0, total = 0
  // This is to avoid copying the metadata files for example
  // .npm is for npm repositories
  // repodata is for yum repositories
  // dists is for debian repositories
  def localGeneratedPaths = [".npm", "repodata", "dists"]
  for (repopair in cfg.entrySet()) {
    def src = repopair.key, dest = repopair.value
    def quer = new JsonBuilder([type: 'file', repo: src]).toString()
    def aql = "items.find($quer).include(\"repo\",\"path\",\"name\")"
    searches.aql(aql.toString()) {
      for (item in it) {
        def path = item.path + '/' + item.name
        if (item.path == '.') path = item.name
        def srcpath = RepoPathFactory.create(src, path)
        def destpath = RepoPathFactory.create(dest, path)
        if (repositories.exists(srcpath) &&
            (!repositories.exists(destpath) ||
             (repositories.getFileInfo(srcpath).checksumsInfo.sha1 !=
              repositories.getFileInfo(destpath).checksumsInfo.sha1))) {
          total += 1
          try {
            if (localGeneratedPaths.any { path.startsWith(it) }){
              continue
            }
            repositories.copy(srcpath, destpath)
            complete += 1
          } catch (Exception ex) {
            log.warn("Unable to backup $srcpath to $dest: $ex.message")
          }
        }
      }
    }
  }
  return [complete, total]
}

def isRemoteBackupConfigExists(String configFilePath) {
  def configFile = new File(configFilePath)
  if (!configFile.exists()) {
    log.warn("File $configFilePath does not exist")
    return false
  }
  return true
}

def getRemoteBackupConfigJSON(String configFilePath) {
  try {
    def configFile = new File(configFilePath)
    def cfg = new JsonSlurper().parse(configFile)
    return cfg
  } catch (JsonException e) {
    log.warn("File $configFilePath is not a valid JSON file: {}", e.message)
    return null
  }
}
