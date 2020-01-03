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

import org.artifactory.fs.ItemInfo
import org.artifactory.repo.RepoPath
import org.artifactory.repo.RepoPathFactory

import groovy.transform.Field

@Field final String PROPERTIES_FILE_PATH = "plugins/${this.class.name}.properties"

def config = new ConfigSlurper().parse(new File(ctx.artifactoryHome.etcDir, PROPERTIES_FILE_PATH).toURI().toURL())
def repositoriesMSCWR = [:]

log.info "Check 'Maven Snapshot Cleanup When Release' plugin repositories list: $config.repositories"

config.repositories.each{ repositorySettings ->
    addRepositoriesSettings(repositoriesMSCWR, repositorySettings)
}

// curl command example for managing configuration of this plugin (prior Artifactory 5.x, use pipe '|' and not semi-colons ';' for parameters separation).
//
// curl -i -uadmin:password -X POST "http://localhost:8081/artifactory/api/plugins/execute/mavenSnapshotCleanupWhenReleaseConfig?params=action=reset;repositories=%5B%5B%22maven-local-lib-releases%22%5C%2C%22maven-local-lib-snapshots%22%5D%5C%2C%5B%22maven-local-plugin-releases%22%5C%2C%22maven-local-plugin-snapshots%22%5D%5D"
//
executions {
    mavenSnapshotCleanupWhenReleaseConfig() { params ->
        log.info "Update configuration with parameters: " + params
        def action = params['action'] ? params['action'][0] as String : "add"
        def repositoriesString = "[]"
        if (params['repositories']) {
            if (params['repositories'].size == 1) {
                repositoriesString = params['repositories'][0]
            } else {
                repositoriesString = params['repositories'].join(',')
            }
        }
        def configRepositories = new ConfigSlurper().parse('repositories=' + repositoriesString)

        if (action == 'reset'){
            log.info "Reseting configuration before adds"
            repositoriesMSCWR.clear()
        }

        configRepositories.repositories.each{repositorySettings -> addRepositoriesSettings(repositoriesMSCWR, repositorySettings) }
    }
}

storage {

    // Standard use case, Maven release created in repository
    afterCreate { ItemInfo item ->
        removeMavenSnapshotIfRelease(item.getRepoPath(), repositoriesMSCWR)
    }

    // Promotion usage, Maven release moved from snapshot (but POM is not updated, should be done by another job => experimental)
    afterMove { ItemInfo item, RepoPath targetRepoPath, properties ->
        removeMavenSnapshotIfRelease(targetRepoPath, repositoriesMSCWR)
    }

    // Not used in this case, the pom file should be renamed with SNAPSHOT or timestamp => afterMove use case
    // afterCopy {}

}

private void addRepositoriesSettings(def repositoriesMSCWR, def repositorySettings){
    def release = repositorySettings[ 0 ] ? repositorySettings[ 0 ] as String : ["undefined"]
    def snapshot = repositorySettings[ 1 ] ? repositorySettings[ 1 ] as String : ["undefined"]

    def releaseConfig = repositories.getRepositoryConfiguration(release)
    def snapshotConfig = repositories.getRepositoryConfiguration(snapshot)

    if (releaseConfig && 'maven' == releaseConfig.getPackageType() && releaseConfig.isHandleReleases() && snapshotConfig && 'maven' == snapshotConfig.getPackageType() && snapshotConfig.isHandleSnapshots()){
        log.debug "Add repositories couple $release/$snapshot"
        repositoriesMSCWR[ release ] = snapshot
    }else{
        log.error "Skip repositories couple $release/$snapshot (does not exist or incorrect handle policy / package type)"
    }
}

private void removeMavenSnapshotIfRelease(RepoPath repoPath, def repositoriesMSCWR){
    if (repositoriesMSCWR [ repoPath.getRepoKey() ] &&  ! repoPath.isFolder() && repoPath.getPath().endsWith('.pom')){
        def parent = new File(repoPath.path).parent + '-SNAPSHOT/'
        def snapshotRepoPath = RepoPathFactory.create(repositoriesMSCWR[repoPath.repoKey], parent)
        if (repositories.exists(snapshotRepoPath)){
            if (log.isInfoEnabled()){
                log.info "Snapshot deletion due to release " + snapshotRepoPath
            }
            repositories.delete(snapshotRepoPath)
        }
    }
}
