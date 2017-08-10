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
import org.apache.commons.compress.archivers.ArchiveInputStream
import org.apache.commons.compress.archivers.ArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.artifactory.repo.RepoPathFactory
import org.artifactory.repo.RepoPath
import org.artifactory.schedule.CachedThreadPoolTaskExecutor

import java.util.regex.Matcher
import java.util.zip.GZIPOutputStream

// Artifactory task executor
@Field def executor = ctx.beanForType(CachedThreadPoolTaskExecutor)

// TODO: Use json file to configure plugin
@Field final def INDEXING_STATUS_PROPERTY = 'cran.indexing.status'
@Field final def INDEXING_PACKAGES_PROPERTY = 'cran.indexing.packages'
@Field final def INDEXING_TIME_PROPERTY = 'cran.indexing.time'
@Field final def INDEXING_LAST_EXECUTION_PROPERTY = 'cran.indexing.last_execution'
@Field final def INDEXING_SILENT_TIME = 5000

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
        if (isCranRepo(item.repoPath.repoKey) && isCranPackage(item.repoPath)) {
            log.info("Creation of CRAN package ${item.repoPath} detected")
            executor.submit {
                storeCranPackageMetadata(item.repoPath)
                // If package was uploaded to the root move it to the right location
                if (item.repoPath.parent.root) {
                    enforceRepositoryLayout(item.repoPath)
                } else {
                    // Otherwise request indexing if needed
                    requestIndexingIfNeeded(item.repoPath)
                }
            }
        }
    }

    /**
     *
     */
    afterMove { item, targetRepoPath, properties ->
        if (isCranRepo(targetRepoPath.repoKey) && isCranPackage(targetRepoPath)) {
            log.info("Move of CRAN package ${targetRepoPath} detected")
            executor.submit {
                requestIndexingIfNeeded(targetRepoPath)
            }
        }
    }

    /**
     *
     */
    afterCopy { item, targetRepoPath, properties ->
        log.debug("afterCopy")
//        requestIndexingIfNeeded(targetRepoPath)
    }

    /**
     *
     */
    afterDelete { item ->
        log.debug("afterDelete")
//        requestIndexingIfNeeded(item.repoPath)
    }
}

/**
 * Enforce CRAN layout to a package deployed at the root folder
 * @param packagePath (RepoPath) - RepoPath to the package
 */
def enforceRepositoryLayout(RepoPath packagePath) {
    try {
        log.info("Enforcing CRAN layout for package $packagePath")
        def packageType = getCranPackageType(packagePath)
        if (packageType != null) {
            log.debug("Package type detected: $packageType")
            def targetPath = null
            switch (packageType) {
                case 'source':
                    targetPath = 'src/contrib'
                    break;
                case 'macosx':
                    def rVersion = getCranBinaryPackageRVersion(packagePath)
                    targetPath = "bin/macosx/el-capitan/contrib/$rVersion"
                    break
                case 'windows':
                    def rVersion = getCranBinaryPackageRVersion(packagePath)
                    targetPath = "bin/windows/contrib/$rVersion"
                    break
            }
            // Move package to right location
            RepoPath targetRepoPath = RepoPathFactory.create(packagePath.repoKey, "$targetPath/${packagePath.path}")
            log.info("New package location: $targetRepoPath")
            repositories.move(packagePath, targetRepoPath)
        }
    } catch (Exception e) {
        log.error("Exception while enforcing CRAN repository layout for package $packagePath", e)
    }
}

/**
 * Return the type of the CRAN package
 * @param packagePath (RepoPath) - RepoPath to the package
 * @return (String) - The type of the CRAN package
 *      "source"  - If package is a source package
 *      "windows" - If package is a windows binary package
 *      "macosx"  - If package is a macosx binary package
 *      null      - If package type cannot be determined
 */
def getCranPackageType(RepoPath packagePath) {
    if (packagePath.path.endsWith('.tar.gz')) {
        return 'source'
    }
    else if (packagePath.path.endsWith('.tgz')) {
        return 'macosx'
    }
    else if (packagePath.path.endsWith('.zip')) {
        return 'windows'
    }

    log.warn("Could not identify package type for $packagePath")
    return null
}

/**
 * Get R version used to built CRAN binary package.
 * The Built property in the DESCRIPTION file is used to get this information
 * @param packagePath (RepoPath) - RepoPath to the package
 * @return (String) R version in the format x.y
 */
def getCranBinaryPackageRVersion(RepoPath packagePath) {
    log.trace("Getting R version for package $packagePath")
    def metadata = getCranPackageMetadataMap(packagePath)
    def rVersion = null
    if (metadata != null) {
        def builtValues = metadata['Built']
        if (builtValues != null) {
            for (value in builtValues) {
                log.trace("Built value: $value")
                Matcher matcher = value =~ /R (\d+).(\d+)/
                if (matcher) {
                    rVersion = matcher.group(1) + '.' + matcher.group(2)
                    log.trace("R version found $rVersion")
                    return rVersion
                }
            }
        }
    }
    log.warn("R version not found for package $packagePath")
    return rVersion
}

/**
 * Check indexing status (property cran.indexing.status) and start new indexing job if needed.
 *
 * If the status is null, 'done' or 'failed', will set the status to 'scheduled' and submit a new indexing job
 * If the status is 'running', will set the status back to 'schedule' but will not submit a new job, at the
 * end of the active execution, the job will check if a new request was received and will submit a new
 * execution.
 *
 * @param artifactPath (RepoPath) - RepoPath to the package
 */
def requestIndexingIfNeeded(RepoPath artifactPath) {
    def repoPath = artifactPath.parent

    // TODO: When handling a deletion check if PACKAGES file exists

    log.debug("Verifying indexing status for repo $repoPath")
    // Get indexing job status
    def status = repositories.getProperty(repoPath, INDEXING_STATUS_PROPERTY)
    log.debug("Current status is $status")
    if (status == null || status == 'done' || status == 'failed') {
        log.debug("Changing indexing status to 'scheduled' and submitting indexing job")
        repositories.setProperty(repoPath, INDEXING_STATUS_PROPERTY, 'scheduled')
        executor.submit {
            indexRepository(repoPath)
        }
    }
    else if (status == 'running') {
        log.debug("Changing indexing status to 'scheduled'")
        repositories.setProperty(repoPath, INDEXING_STATUS_PROPERTY, 'scheduled')
    } else {
        log.debug("Indexing already scheduled, ignoring request")
    }
}

/**
 * Index CRAN repository
 * @param repositoryPath (RepoPath) - RepoPath of the repository folder containing CRAN artifacts
 */
def indexRepository(RepoPath repositoryPath) {
    try {
        log.info("Indexing CRAN repository $repositoryPath")

        // TODO: check if repository still exists

        log.debug("Going silent for a while so packages being deployed can be available")
        sleep(INDEXING_SILENT_TIME)

        log.debug("Changing indexing status to 'running'")
        repositories.setProperty(repositoryPath, INDEXING_STATUS_PROPERTY, 'running')

        def indexInitialTime = System.currentTimeMillis()
        def totalPackages = 0

        // Concatenate children metadata
        def repositoryMetadataContent = []
        def children = repositories.getChildren(repositoryPath)
        log.debug("Repository size: ${children?.size()}")
        for (item in children) {
            log.trace("Indexing package: ${item.repoPath}")
            if (isCranPackage(item.repoPath)) {
                totalPackages++
                def packageMetadata = getPackageMetadata(item.repoPath)
                if (packageMetadata != null) {
                    repositoryMetadataContent = (repositoryMetadataContent << packageMetadata.bytes << System.lineSeparator().bytes).flatten()
                }
            }
        }

        log.debug("Writing index of repository $repositoryPath")
        // Write repository metadata file
        def repositoryMetadataFile = RepoPathFactory.create(repositoryPath.getRepoKey(), "${repositoryPath.getPath()}/PACKAGES")
        repositories.deploy(repositoryMetadataFile, new ByteArrayInputStream(repositoryMetadataContent as byte[]))

        // Write compressed repository metadata file
        def compressedMetadataFile = RepoPathFactory.create(repositoryPath.getRepoKey(), "${repositoryPath.getPath()}/PACKAGES.gz")
        repositories.deploy(compressedMetadataFile, new ByteArrayInputStream(getGzipCompressedByteArray(repositoryMetadataContent as byte[])))

        def indexFinalTime = System.currentTimeMillis()
        def totalTime = indexFinalTime - indexInitialTime

        log.debug("Finished indexing CRAN repository $repositoryPath in $totalTime milliseconds")
        repositories.setProperty(repositoryPath, INDEXING_PACKAGES_PROPERTY, totalPackages as String)
        repositories.setProperty(repositoryPath, INDEXING_TIME_PROPERTY, totalTime as String)
        repositories.setProperty(repositoryPath, INDEXING_LAST_EXECUTION_PROPERTY, new Date().toString())

        def status = repositories.getProperty(repositoryPath, INDEXING_STATUS_PROPERTY)
        if (status == 'scheduled') {
            log.info("New indexing request detected. Submitting job again")
            executor.submit {
                indexRepository(repositoryPath)
            }
        } else {
            repositories.setProperty(repositoryPath, INDEXING_STATUS_PROPERTY, 'done')
        }

    } catch (Exception e) {
        log.error("Exception while indexing CRAN repository $repositoryPath", e)
        repositories.setProperty(repositoryPath, INDEXING_STATUS_PROPERTY, 'failed')
    }
}

/**
 * Check if repository is marked as a CRAN repository
 * @param repoKey (String) - Repository key
 * @return Boolean
 */
def isCranRepo(String repoKey) {
    log.trace("Checking if $repoKey is a CRAN repository")
    RepoPath repositoryPath = RepoPathFactory.create(repoKey, '')
    def result = repositories.hasProperty(repositoryPath, 'cran')
    log.trace("$repoKey IS ${!result?'NOT ':''}a CRAN repository")
    return result
}

/**
 * Check if the artifact is a CRAN package
 * @param artifactPath (RepoPath) - RepoPath of the artifact
 * @return Boolean
 */
def isCranPackage(RepoPath artifactPath) {
    log.trace("Checking if $artifactPath is a CRAN package")
    def result =  artifactPath.isFile() &&
            ( artifactPath.path.endsWith('.zip') ||
                    artifactPath.path.endsWith('.tar.gz') ||
                    artifactPath.path.endsWith('.tgz')
            )
    log.trace("$artifactPath IS ${!result?'NOT ':''}a CRAN package")
    return result
}

/**
 * Store CRAN package metadata as artifact properties
 * @param packagePath (RepoPath) - RepoPath to the package
 */
def storeCranPackageMetadata(RepoPath packagePath) {
    log.info("Storing CRAN package metadata for package $packagePath")
    def metadataMap = getCranPackageMetadataMap(packagePath)
    if (metadataMap != null) {
        log.trace("Metadata: $metadataMap")
        metadataMap.each { key, values ->
            String propertyKey = key.toLowerCase()
            String propertyValue = values.join('')
            propertyValue = propertyValue.replace('\t', ' ')
            log.trace("Adding property $propertyKey with value $propertyValue")
            repositories.setProperty(packagePath, "cran.$propertyKey", propertyValue)
        }
    }
}

/**
 * Get package metadata as a Map of multivalued properties
 * @param packagePath (RepoPath) - RepoPath of the package
 * @return Map
 */
def getCranPackageMetadataMap(RepoPath packagePath) {
    String metadata = getPackageMetadata(packagePath)
    if (metadata != null) {
        def lines = metadata.split(System.lineSeparator())
        def result = [:]
        def currentProperty = null
        for (line in lines) {
            // If line starts with space or tab the previous property was a multiline one. Append content to last property
            if (line.startsWith(' ') || line.startsWith('\t')) {
                result[currentProperty].add(line)
            } else {
                // Otherwise add new property to map
                def indexOfSeparator = line.indexOf(':')
                currentProperty = line.substring(0, indexOfSeparator)
                result[currentProperty] = []
                result[currentProperty].add(line.substring(indexOfSeparator + 1).trim())
            }
        }
        return result
    }
    return null
}

/**
 * Get Package Metadata content
 * @param artifactPath (RepoPath) - RepoPath of the artifact
 * @return String
 */
def getPackageMetadata(RepoPath artifactPath) {
    log.trace("Fetching CRAN package metadata")
    try {
        InputStream packageStream = repositories.getContent(artifactPath).getInputStream()
        def metadata = null
        packageStream.withCloseable {
            // Get archive input stream accordingly to the file type
            ArchiveInputStream archiveStream = null
            if (artifactPath.path.endsWith('.zip')) {
                archiveStream = new ZipArchiveInputStream(packageStream)
            } else {
                archiveStream = new TarArchiveInputStream(new GzipCompressorInputStream(packageStream))
            }
            // Search entries for DESCRIPTION file
            for (ArchiveEntry entry = archiveStream.getNextEntry(); entry != null; entry = archiveStream.getNextEntry()) {
                if (entry.name ==~ /.*\/DESCRIPTION$/) {
                    log.trace("Metadata file found!")
                    metadata = archiveStream.getText()
                    return
                }
            }
        }
        if (metadata == null) {
            log.warn("Metadata not found for package $artifactPath")
        }
        return metadata
    } catch (Exception e) {
        log.error("Exception while reading package metadata from $artifactPath", e)
        return null
    }
}

/**
 * GZIP Compress byte array
 * @param bytes (byte[]) - Byte array to be compressed
 * @return (byte[]) - GZIP Compressed byte array
 */
def getGzipCompressedByteArray(byte[] bytes) {
    def byteArrayOutputStream = new ByteArrayOutputStream()
    def gzipOutputStream = new GZIPOutputStream(byteArrayOutputStream)
    try {
        gzipOutputStream.write(bytes, 0, bytes.length)
        gzipOutputStream.finish()
        gzipOutputStream.flush()
        byteArrayOutputStream.flush()
        return byteArrayOutputStream.toByteArray()
    } finally {
        if (gzipOutputStream != null) {
            gzipOutputStream.close()
        }
        if (byteArrayOutputStream != null) {
            byteArrayOutputStream.close()
        }
    }
}