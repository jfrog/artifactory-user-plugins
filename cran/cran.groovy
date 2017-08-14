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

@Field def executor = ctx.beanForType(CachedThreadPoolTaskExecutor)

class CRANConstants {
    static final String CRAN_REPO_PROPERTY = 'cran'
    static final String INDEXING_STATUS_PROPERTY = 'cran.indexing.status'
    static final String INDEXING_PACKAGES_PROPERTY = 'cran.indexing.packages'
    static final String INDEXING_TIME_PROPERTY = 'cran.indexing.time'
    static final String INDEXING_LAST_EXECUTION_PROPERTY = 'cran.indexing.last_execution'
    static final int INDEXING_SILENT_TIME = 5 * 1000l // 5 seconds
    static final long REMOTE_INDEX_CACHE_TIME = 10 * 60 * 1000l // 10 minutes
}

executions {
    /**
     * Request repository CRAN indexing
     * Parameters:
     * repoKey - Key of CRAN repository
     * path - Path to the artifacts parent folder to index
     */
    cranIndex(params: ['repoKey':[], 'path':[]]) { params ->
        log.debug("cranIndex called with params: $params")

        def repoKey = params['repoKey']?.get(0)
        def path = params['path']?.get(0)

        // Validate arguments
        if (!repoKey) {
            message = "Please inform the 'repoKey'"
            status = 400
            return
        }
        if (!path) {
            message = "Please inform the 'path' to be indexed"
            status = 400
            return
        }
        if (!isCranRepo(repoKey)) {
            message = "$repoKey is not a CRAN repository"
            status = 400
            return
        }
        def repoPath = RepoPathFactory.create(repoKey, path)
        if (!repositories.exists(repoPath)) {
            message = "CRAN repository $repoPath does not exist"
            status = 400
            return
        }
        def itemInfo = repositories.getItemInfo(repoPath)
        if (!itemInfo.isFolder()) {
            message = "$repoPath is not a folder"
            status = 400
            return
        }

        log.info("Indexing request received for CRAN repo $repoPath")
        repositories.setProperty(repoPath, CRANConstants.INDEXING_STATUS_PROPERTY, 'scheduled')
        executor.submit {
            indexRepository(repoPath)
        }
        status = 200
        message = "CRAN indexing execution requested for repo $repoPath"
    }
}

download {
    /**
     * Expires remote repository cached index so the remote file will be used if available
     */
    beforeDownloadRequest { request, repoPath ->
        log.trace ("beforeDownloadRequest: $repoPath")
        if (repositories.getRemoteRepositories().contains(repoPath.getRepoKey()) &&
                isRepositoryIndex(repoPath) &&
                isFileExpired(repoPath)) {
            log.info("Expiring remote repository index at " + repoPath)
            expired = true
        }
    }
}

storage {
    /**
     * Handle creation of CRAN packages.
     *
     * At creation, extract package metadata and save it as artifact properties.
     * If package is deployed at the repository's root folder, move it to the
     * right location, according to package type. Otherwise, request indexing if needed.
     */
    afterCreate { item ->
        log.trace("afterCreate: $item")
        if (isCranRepo(item.repoPath.repoKey) && isCranPackage(item.repoPath)) {
            log.info("Creation of CRAN package ${item.repoPath} detected")
            storeCranPackageMetadata(item.repoPath)
            if (item.repoPath.parent.root) {
                executor.submit {
                    // If package was uploaded to the root move it to the right location
                    enforceRepositoryLayout(item.repoPath)
                }
            } else {
                // Otherwise request indexing if needed
                requestIndexingIfNeeded(item.repoPath, false)
            }
        }
    }

    /**
     * Handle move of CRAN packages
     *
     * Request indexing of the target repository if needed.
     */
    afterMove { item, targetRepoPath, properties ->
        log.trace("afterMove: $targetRepoPath")
        if (isCranRepo(targetRepoPath.repoKey) && isCranPackage(targetRepoPath)) {
            log.info("Move of CRAN package ${targetRepoPath} detected")
            requestIndexingIfNeeded(targetRepoPath, false)
        }
    }

    /**
     * Handle copy of CRAN packages
     *
     * Request indexing of the target repository if needed.
     */
    afterCopy { item, targetRepoPath, properties ->
        log.trace("afterCopy: $targetRepoPath")
        if (isCranRepo(targetRepoPath.repoKey) && isCranPackage(targetRepoPath)) {
            log.info("Copy of CRAN package ${targetRepoPath} detected")
            requestIndexingIfNeeded(targetRepoPath, false)
        }
    }

    /**
     * Handle deletion of CRAN packages
     *
     * Request indexing of the repository if needed. This interceptor is used to handle
     * packages move as well.
     */
    afterDelete { item ->
        log.trace("afterDelete: $item")
        if (isCranRepo(item.repoPath.repoKey) && isCranPackage(item.repoPath)) {
            log.info("Deletion of CRAN package ${item.repoPath} detected")
            requestIndexingIfNeeded(item.repoPath, true)
        }
    }
}

/**
 * Enforce CRAN layout to a package deployed at the root level
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
 * If the status is 'running', will set the status back to 'schedule' but will not submit a new job. At the
 * end of the active execution, the job will check if a new request was received and will submit a new
 * execution.
 * If the status is already 'scheduled' will ignore this request.
 *
 * @param artifactPath (RepoPath) - RepoPath to the package
 * @param reindexOnly (Boolean) - Request indexing only if this folder has already been indexed in the past
 */
def requestIndexingIfNeeded(RepoPath artifactPath, reindexOnly) {
    try {
        def repoPath = artifactPath.parent
        // Get indexing job status
        log.debug("Verifying indexing status for repo $repoPath")
        def status = repositories.getProperty(repoPath, CRANConstants.INDEXING_STATUS_PROPERTY)
        log.debug("Current status is $status")

        if ('scheduled' == status) {
            log.debug("Indexing already scheduled, ignoring request")
            return
        }

        RepoPath indexPath = RepoPathFactory.create(repoPath.repoKey, "${repoPath.path}/PACKAGES")
        if (reindexOnly && !repositories.exists(indexPath)) {
            log.debug("Reindex only request ignored for repo $repoPath")
            return
        }

        if (status == null || status == 'done' || status == 'failed') {
            log.debug("Changing indexing status to 'scheduled' and submitting indexing job")
            repositories.setProperty(repoPath, CRANConstants.INDEXING_STATUS_PROPERTY, 'scheduled')
            executor.submit {
                indexRepository(repoPath)
            }
        } else if (status == 'running') {
            log.debug("Changing indexing status to 'scheduled'")
            repositories.setProperty(repoPath, CRANConstants.INDEXING_STATUS_PROPERTY, 'scheduled')
        }
    } catch(Exception e){
        log.error("Exception while requesting index for package $artifactPath", e)
    }
}

/**
 * Index CRAN repository
 *
 * The index process consists in creating a PACKAGES file which contains the concatenated DESCRIPTION files of
 * all packages inside the repository. The GZIP compressed version of the index file is also created.
 *
 * @param repositoryPath (RepoPath) - RepoPath of the repository folder containing CRAN artifacts
 */
def indexRepository(RepoPath repositoryPath) {
    try {
        log.info("Indexing CRAN repository $repositoryPath")

        log.debug("Going silent for a while so packages being deployed can be available")
        sleep(CRANConstants.INDEXING_SILENT_TIME)

        if (repositories.exists(repositoryPath)) {

            log.debug("Changing indexing status to 'running'")
            repositories.setProperty(repositoryPath, CRANConstants.INDEXING_STATUS_PROPERTY, 'running')

            // Compute indexing time and number of packages indexed
            def indexInitialTime = System.currentTimeMillis()
            def totalPackages = 0

            // Concatenate children metadata
            def repositoryMetadataContent = []
            def lineSeparator = getLineSeparator(repositoryPath)
            def children = repositories.getChildren(repositoryPath)
            log.debug("Repository size: ${children?.size()}")
            for (item in children) {
                log.trace("Indexing package: ${item.repoPath}")
                if (isCranPackage(item.repoPath)) {
                    totalPackages++
                    def packageMetadata = getPackageMetadata(item.repoPath)
                    if (packageMetadata != null) {
                        repositoryMetadataContent = (repositoryMetadataContent << packageMetadata.bytes << lineSeparator.bytes).flatten()
                    }
                }
            }

            // Write index data
            writeRepositoryIndex(repositoryPath, repositoryMetadataContent)

            // Store indexing time and number of package
            def indexFinalTime = System.currentTimeMillis()
            def totalTime = indexFinalTime - indexInitialTime
            log.debug("Finished indexing CRAN repository $repositoryPath in $totalTime milliseconds")
            repositories.setProperty(repositoryPath, CRANConstants.INDEXING_PACKAGES_PROPERTY, totalPackages as String)
            repositories.setProperty(repositoryPath, CRANConstants.INDEXING_TIME_PROPERTY, totalTime as String)
            repositories.setProperty(repositoryPath, CRANConstants.INDEXING_LAST_EXECUTION_PROPERTY, new Date().toString())

            // Change indexing status and request new execution if needed
            def status = repositories.getProperty(repositoryPath, CRANConstants.INDEXING_STATUS_PROPERTY)
            if (status == 'scheduled') {
                log.info("New indexing request detected. Submitting job again")
                indexRepository(repositoryPath)
            } else {
                repositories.setProperty(repositoryPath, CRANConstants.INDEXING_STATUS_PROPERTY, 'done')
            }
        }

    } catch (Exception e) {

        if (!repositories.exists(repositoryPath)) {
            log.debug("Repository $repositoryPath does not exist anymore")
            return
        }

        log.error("Exception while indexing CRAN repository $repositoryPath", e)
        repositories.setProperty(repositoryPath, CRANConstants.INDEXING_STATUS_PROPERTY, 'failed')
    }
}

/**
 * Deploy CRAN repository index to artifactory
 * @param repositoryPath (RepoPath) - RepoPath to CRAN repository
 * @param repositoryMetadataContent (byte[]) - Index content
 */
def writeRepositoryIndex(RepoPath repositoryPath, repositoryMetadataContent) {
    log.debug("Writing index of repository $repositoryPath")
    // Write repository metadata file
    def repositoryMetadataFile = RepoPathFactory.create(repositoryPath.getRepoKey(), "${repositoryPath.getPath()}/PACKAGES")
    repositories.deploy(repositoryMetadataFile, new ByteArrayInputStream(repositoryMetadataContent as byte[]))
    // Write compressed repository metadata file
    def compressedMetadataFile = RepoPathFactory.create(repositoryPath.getRepoKey(), "${repositoryPath.getPath()}/PACKAGES.gz")
    repositories.deploy(compressedMetadataFile, new ByteArrayInputStream(getGzipCompressedByteArray(repositoryMetadataContent as byte[])))
}

/**
 * Check if repository is marked as a CRAN repository
 * @param repoKey (String) - Repository key
 * @return Boolean
 */
def isCranRepo(String repoKey) {
    log.trace("Checking if $repoKey is a CRAN repository")
    RepoPath repositoryPath = RepoPathFactory.create(repoKey, '')
    def result = repositories.localRepositories.contains(repoKey) && repositories.hasProperty(repositoryPath, CRANConstants.CRAN_REPO_PROPERTY)
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
            propertyValue = propertyValue.replaceAll('\\s+', ' ')
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
        def lineSeparator = getLineSeparator(packagePath)
        def lines = metadata.split(lineSeparator)
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
        } else {
            if (!validatePackageMetadataContent(metadata, getLineSeparator(artifactPath))) {
                log.warn("Invalid metadata content found for package $artifactPath")
                metadata = null
            }
        }
        return metadata
    } catch (Exception e) {
        log.error("Exception while reading package metadata from $artifactPath", e)
        return null
    }
}

/**
 * Validate metadata content.
 * The metadata package file (DESCRIPTION) supports the following content:
 *     - Fields start with an ASCII name immediately followed by a colon: the value starts after the colon and a space.
 *     - Continuation lines (for example, for descriptions longer than one line) start with a space or tab.
 * (Reference: https://cran.r-project.org/doc/manuals/R-exts.html#The-DESCRIPTION-file)
 *
 * @param metadata (String) - The package metadata content
 * @param lineSeparator (String) - Line separator to be used to parse the metadata
 * @return (Boolean) - True if the metadata content is valid. False otherwise.
 */
def validatePackageMetadataContent(metadata, lineSeparator) {
    def lines = metadata.split(lineSeparator)
    for (def line in lines) {
        if (!line.isEmpty() && !(line ==~ /^(([^\s]+: .*)|([\t ]+.*))$/)) {
            log.debug("Invalid metadata line found |$line|")
            return false
        }
    }
    return true
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

/**
 * Get lineSeparator based on artifact path
 * @param repositoryPath (RepoPath) - Path to the artifact
 * @return
 *     '\r\n' - if artifact is a zip file or if the path contains 'windows' in its composition.
 *     '\n'   - otherwise.
 */
def getLineSeparator(repositoryPath) {
    if (repositoryPath.path.endsWith('.zip') || (repositoryPath.path =~ /(^|\/)windows(\/|$)/)) {
        log.trace 'Using windows line separator'
        return '\r\n'
    } else {
        log.trace 'Using unix line separator'
        return '\n'
    }
}

/**
 * Check if the path represents a CRAN repository index file
 * @param repoPath (RepoPath)
 * @return True if the repoPath represents a CRAN repository index file and its compressed versions. False otherwise.
 */
def isRepositoryIndex(RepoPath repoPath) {
    log.trace "Checking if path represents a CRAN repository index file: $repoPath"
    def result = repoPath.path.endsWith('PACKAGES') ||
            repoPath.path.endsWith('PACKAGES.gz') ||
            repoPath.path.endsWith('PACKAGES.rds')
    log.debug "$repoPath IS ${!result?'NOT ':''}a repository index"
    return result
}

/**
 * Check if file is older than the configured cache time
 * @param indexPath (RepoPath) - Path to the file
 * @return True if the file's lastUpdated time is more then REMOTE_INDEX_CACHE_TIME milliseconds old. False otherwise.
 */
def isFileExpired(RepoPath indexPath) {
    log.debug "Checking if remote index file must be expired $indexPath"
    if (!repositories.exists(indexPath)) {
        return false
    }
    def itemInfo = repositories.getItemInfo(indexPath)
    def lastUpdated = itemInfo.lastUpdated
    log.trace "Index last updated at: $lastUpdated"
    def cacheAge = System.currentTimeMillis() - lastUpdated
    log.trace "Index cache age: $cacheAge"
    def result = cacheAge > CRANConstants.REMOTE_INDEX_CACHE_TIME
    log.debug "$indexPath IS ${!result?'NOT ':''}expired"
    return result
}