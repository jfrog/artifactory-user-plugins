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

import groovy.transform.Field
import org.artifactory.storage.db.DbService
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.artifactory.repo.RepoPathFactory
import org.artifactory.repo.RepoPath
import org.artifactory.schedule.CachedThreadPoolTaskExecutor

// Artifactory task executor
@Field def executor = ctx.beanForType(CachedThreadPoolTaskExecutor)

executions {
    /**
     * Request repository CRAN indexing
     * Parameters:
     * repoPath - Path to the artifacts parent folder to index
     */
    cranIndex(params: ['repoPath':[]]) { params ->
        log.debug('cranIndex called')
        log.debug("params: $params")
    }
}

download {

    /**
     *
     */
    beforeDownloadRequest { request, repoPath ->
        //TODO: Expire metada for remote repositories
    }

}

storage {
    /**
     *
     */
    afterCreate { item ->
        log.debug("afterCreate")
        requestIndexingIfNeeded(item.repoPath)

    }

    /**
     *
     */
    afterMove { item, targetRepoPath, properties ->
        log.debug("afterMove")
        requestIndexingIfNeeded(targetRepoPath)
    }

    /**
     *
     */
    afterCopy { item, targetRepoPath, properties ->
        log.debug("afterCopy")
        requestIndexingIfNeeded(targetRepoPath)
    }
}

/**
 * Request indexing of artifact's folder if needed.
 * Will request indexing if:
 *     - Repo is configured as a CRAN repository
 *     - Artifact is a CRAN package
 * @param artifactPath (RepoPath) - RepoPath of the artifact
 */
def requestIndexingIfNeeded(RepoPath artifactPath) {
    executor.submit {
        if (isCranRepo(artifactPath.getRepoKey()) && isCranPackage(artifactPath)) {
            // TODO: Check for concurrent indexing jobs
            indexRepository(artifactPath.parent)
        }
    }
}

/**
 * Check if repository is marked as a CRAN repository
 * @param repoKey (String) - Repository key
 */
def isCranRepo(String repoKey) {
    // TODO: Review log verbosity
    log.debug("Checking if $repoKey is a CRAN repository...")
    RepoPath repositoryPath = RepoPathFactory.create(repoKey, '')
    def result = repositories.hasProperty(repositoryPath, 'cran')
    log.debug("$repoKey IS ${!result?'NOT ':''}a CRAN repository")
    return result
}

/**
 * Check if the artifact is a CRAN package
 * @param artifactPath (RepoPath) - RepoPath of the artifact
 */
def isCranPackage(RepoPath artifactPath) {
    log.debug("Checking if $artifactPath is a CRAN package...")
    def result =  artifactPath.isFile() &&
            ( artifactPath.path.endsWith('.zip') ||
                    artifactPath.path.endsWith('.tar.gz') ||
                    artifactPath.path.endsWith('.tgz')
            )
    log.debug("$artifactPath IS ${!result?'NOT ':''}a CRAN package")
    return result
}

/**
 * Index CRAN repository
 * @param repositoryPath (RepoPath) - RepoPath of the repository folder containing CRAN artifacts
 */
def indexRepository(RepoPath repositoryPath) {
    try {
        log.debug("Indexing CRAN repository $repositoryPath ...")
        // Concatenate children metadata
        def repositoryMetadataContent = []
        def children = repositories.getChildren(repositoryPath)
        for (item in children) {
            if (isCranPackage(item.repoPath)) {
                def packageMetadata = getPackageMetadata(item.repoPath)
                if (packageMetadata != null) {
                    repositoryMetadataContent = (repositoryMetadataContent << packageMetadata.bytes << System.lineSeparator().bytes).flatten()
                }
            }
        }
        // Write repository metadata file
        def repositoryMetadataFile = RepoPathFactory.create(repositoryPath.getRepoKey(), "${repositoryPath.getPath()}/PACKAGES")
        repositories.deploy(repositoryMetadataFile, new ByteArrayInputStream(repositoryMetadataContent as byte[]))

        // TODO: Generate compressed metadata file

        log.debug("Finished indexing CRAN repository $repositoryPath")

        // TODO: Check if indexing can be already expired

    } catch (Exception e) {
        log.error("Exception while indexing CRAN repository $repositoryPath", e)
    }
}

/**
 * Get Package Metadata content
 * @param artifactPath (RepoPath) - RepoPath of the artifact
 */
def getPackageMetadata(RepoPath artifactPath) {
    try {
        log.debug("Fetching CRAN package metadata...")

        // TODO: Check file type and add treatment to zip files

        InputStream packageStream = repositories.getContent(artifactPath).getInputStream()
        TarArchiveInputStream tarStream = new TarArchiveInputStream(new GzipCompressorInputStream(packageStream))
        def metadata = null
        tarStream.withCloseable {
            for (TarArchiveEntry tarEntry = tarStream.getNextTarEntry(); tarEntry != null; tarEntry = tarStream.getNextTarEntry()) {
                def isMetadataFile = tarEntry.name ==~ /.*\/DESCRIPTION$/
                if (isMetadataFile) {
                    log.debug("Metadata file found!")
                    metadata = tarStream.getText()
                    return
                }
            }
        }

        if (metadata == null) {
            log.warn("Metadata not found!")
        }
        return metadata
    } catch (Exception e) {
        log.error("Exception while reading package metadata from $artifactPath", e)
        return null
    }
}
