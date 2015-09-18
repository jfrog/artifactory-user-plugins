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

import org.artifactory.exception.CancelException
import org.artifactory.fs.StatsInfo
import org.artifactory.md.Properties
import org.artifactory.repo.RepoPath
import org.artifactory.repo.RepoPathFactory

/**
 * Example REST call(s):
 * 1. Archive any artifact over 30 days old:
 *    curl -X POST -v -u <admin_user>:<admin_password> "http://localhost:8080/artifactory/api/plugins/execute/archive_old_artifacts?params=ageDays=30"
 * 2. Archive any artifact that is 30 days old and has the following properties set:
 *    curl -X POST -v -u <admin_user>:<admin_password> "http://localhost:8080/artifactory/api/plugins/execute/archive_old_artifacts?params=ageDays=30|includePropertySet=deleteme:true;junk:true"
 * 3. Archive any artifact that has not been downloaded in 60 days, excluding those with a certain property set:
 *    curl -X POST -v -u <admin_user>:<admin_password> "http://localhost:8080/artifactory/api/plugins/execute/archive_old_artifacts?params=lastDownloadedDays=60|excludePropertySet=keeper:true"
 * 4. Archive only *.tgz files that are 30 days old and have not been downloaded in 15 days:
 *    curl -X POST -v -u <admin_user>:<admin_password> "http://localhost:8080/artifactory/api/plugins/execute/archive_old_artifacts?params=filePattern=*.tgz|ageDays=30|lastDownloadedDays=15"
 * 5. Archive any *.tgz artifact that is 30 days old and is tagged with artifact.delete:
 *    curl -X POST -v -u <admin_user>:<admin_password> "http://localhost:8080/artifactory/api/plugins/execute/archive_old_artifacts?params=filePattern=*.tgz|ageDays=30|includePropertySet=artifact.delete"
 * 6. Archive any *.tgz artifact that is 15 days old and is tagged with artifact.delete=true:
 *    curl -X POST -v -u <admin_user>:<admin_password> "http://localhost:8080/artifactory/api/plugins/execute/archive_old_artifacts?params=filePattern=*.tgz|ageDays=15|includePropertySet=artifact.delete:true"
 *
 * Available 'time period' archive policies:
 * 1. lastModified      the last time the artifact was modified
 * 2. lastUpdated       the last time the artifact was updated
 * 3. created           the creation date of the artifact
 * 4. lastDownloaded    the last time the artifact was downloaded
 * 5. age               the age of the artifact
 * (NOTE: the time period archive policies are all specified in number of days)
 *
 * Available 'property' archive policies:
 * 1. includePropertySet   the artifact will be archived if it possesses all of
 *    the passed in properties
 * 2. excludePropertySet   the artifact will not be archived if it possesses all
 *    of the passed in properties
 * (NOTE: property set format ==> prop[:value1[;prop2[:value2]......[;propN[:valueN]]])
 *        A property key must be provided, but a corresponding value is not necessary.
 *        If a property is set without a value, then a check is made for just the key.
 *
 * Available artifact keep policy:
 * 1. numKeepArtifacts      the number of artifacts to keep per directory
 * (NOTE: This allows one to keep X number of artifacts (based on natural directory sort
 *        per directory. So, if your artifacts are lain out in a flat directory structure,
 *        you can keep the last X artifacts in each directory with this setting.))
 *
 * One can set any number of 'time period' archive policies as well as any number of include and exclude
 * attribute sets. It is up to the caller to decide how best to archive artifacts. If no archive policy
 * parameters are sent in, the plugin aborts in order to not allow default deleting of every artifact.
 *
 * The 'archive' process performs the following:
 * 1. Grabs all of the currently set properties on the artifact
 * 2. Does a deploy over top of the artifact with a 1-byte size file (to conserve space)
 * 3. Adds all of the previously held attributes to the newly deployed 1-byte size artifact
 * 4. Moves the artifact from the source repository to the destination repository specified
 * 5. Adds a property containing the archive timestamp to the artifact
 *
 * @author Adam Kunk
 */

executions {
    archive_old_artifacts { params ->
        def filePattern = params['filePattern'] ? params['filePattern'][0] as String : '*'
        def srcRepo = params['srcRepo'] ? params['srcRepo'][0] as String : 'build-packages'
        def archiveRepo = params['archiveRepo'] ? params['archiveRepo'][0] as String : 'build-packages-archived'
        def lastModifiedDays = params['lastModifiedDays'] ? params['lastModifiedDays'][0] as int : 0
        def lastUpdatedDays = params['lastUpdatedDays'] ? params['lastUpdatedDays'][0] as int : 0
        def createdDays = params['createdDays'] ? params['createdDays'][0] as int : 0
        def lastDownloadedDays = params['lastDownloadedDays'] ? params['lastDownloadedDays'][0] as int : 0
        def ageDays = params['ageDays'] ? params['ageDays'][0] as int : 0
        def excludePropertySet = params['excludePropertySet'] ? params['excludePropertySet'][0] as String : ''
        def includePropertySet = params['includePropertySet'] ? params['includePropertySet'][0] as String : ''
        def archiveProperty = params['archiveProperty'] ? params['archiveProperty'][0] as String : 'archived.timestamp'
        def numKeepArtifacts = params['numKeepArtifacts'] ? params['numKeepArtifacts'][0] as int : 0

        archiveOldArtifacts(
            log,
            filePattern,
            srcRepo,
            archiveRepo,
            lastModifiedDays,
            lastUpdatedDays,
            createdDays,
            lastDownloadedDays,
            ageDays,
            excludePropertySet,
            includePropertySet,
            archiveProperty,
            numKeepArtifacts)
    }
}

class ArchiveConstants {
    // Variable to translate days to milliseconds (24 * 60 * 60 * 1000)
    final static DAYS_TO_MILLIS = 86400000
    // HTTP status code response for OK
    final static HTTP_OK = 200
}

/**
 * Function to archive old build artifacts by moving them from a source repository to an "archive"
 * repository based on some archive policy (how old the artifact is, when it was last updated, the
 * last time it was downloaded, properties the artifact has, etc.). The artifact is re-deployed
 * with a 1-byte size file to preserve the record in Artifactory for auditing and history purposes.
 * All of the properties are preserved and some additional ones are set.
 *
 * This is done to preserve disk space on the server while keeping all auditing and history about
 * the artifacts that are archived. An example usage is to archive old product build artifacts
 * that are not used in a meaningful way within an organization.
 */
private archiveOldArtifacts(
    log,
    filePattern,
    srcRepo,
    archiveRepo,
    lastModifiedDays,
    lastUpdatedDays,
    createdDays,
    lastDownloadedDays,
    ageDays,
    excludePropertySet,
    includePropertySet,
    archiveProperty,
    numKeepArtifacts) {
    log.warn('Starting archive process for old artifacts ...')
    log.info('File match pattern: {}', filePattern)
    log.info('Source repository: {}', srcRepo)
    log.info('Archive repository: {}', archiveRepo)
    log.info('Archive after last modified days: {}', lastModifiedDays)
    log.info('Archive after last updated days: {}', lastUpdatedDays)
    log.info('Archive after created days: {}', createdDays)
    log.info('Archive after last downloaded days: {}', lastDownloadedDays)
    log.info('Archive after age days: {}', ageDays)
    log.info('Exclude property set: {}', excludePropertySet)
    log.info('Include property set: {}', includePropertySet)
    log.info('Archive property: {}', archiveProperty)
    log.info('Number of artifacts to keep per directory: {}', numKeepArtifacts)

    // Abort if no selection criteria was sent in (we don't want to archive everything blindly)
    if (lastModifiedDays == 0 &&
        lastUpdatedDays == 0 &&
        createdDays == 0 &&
        lastDownloadedDays == 0 &&
        ageDays == 0 &&
        excludePropertySet == '' &&
        includePropertySet == '') {
        log.error('No selection criteria specified, exiting now!')
        throw new CancelException('No selection criteria specified!', 400)
    }

    // Booleans verifying whether or not to archive the artifact
    boolean archiveTiming = true
    boolean archiveExcludeProperties = true
    boolean archiveIncludeProperties = true
    // Total count of artifacts that were archived
    int artifactsArchived = 0

    artifactsCleanedUp =
        searches.artifactsByName(filePattern, srcRepo).each { artifact ->
            log.info('Search found artifact: {}', artifact)

            // Get times
            def todayDate = new Date()
            def todayTime = todayDate.time
            log.info("Today's date: {}", todayDate)

            // Check out the content size
            def content = repositories.getContent(artifact)
            def contentSize = content.getSize()
            log.info('Artifact {} has contentSize: {}', artifact, contentSize)
            content.close()

            // Get the ItemInfo about the item
            def itemInfo = repositories.getItemInfo(artifact)
            log.info('Artifact {} item info: {}', artifact, itemInfo)

            // Check if we are to perform a timing policy check
            if (lastModifiedDays != 0 ||
                lastUpdatedDays != 0 ||
                createdDays != 0 ||
                lastDownloadedDays != 0 ||
                ageDays != 0) {
                log.info('We are going to perform a timing policies check...')

                // Call the function to check if we need to archive based on timing policies
                archiveTiming = checkArchiveTimingPolicies(
                    artifact,
                    lastModifiedDays,
                    lastUpdatedDays,
                    createdDays,
                    lastDownloadedDays,
                    ageDays,
                    itemInfo,
                    todayTime)
            }
            // Check if we are to exclude some artifacts based on attributes
            if (excludePropertySet != '') {
                log.info('We are going to exclude artifacts based on attributes...')
                Map<String, String> excludeMap = translatePropertiesString(excludePropertySet)

                log.info('about to call verify properties for false')
                // Call the function to check if we need to archive based on excluded properties
                archiveExcludeProperties = verifyProperties(artifact, excludeMap, false)
            }
            // Check if we are to include some artifacts based on attributes
            if (includePropertySet != '') {
                log.info('We are going to include artifacts based on attributes...')
                Map<String, String> includeMap = translatePropertiesString(includePropertySet)

                log.info('about to call verify properties for true')
                // Call the function to check if we need to archive based on included properties
                archiveIncludeProperties = verifyProperties(artifact, includeMap, true)
            }

            // Logging
            log.debug('-- archiveTiming: {}', archiveTiming)
            log.debug('-- archiveExcludeProperties: {}', archiveExcludeProperties)
            log.debug('-- archiveIncludeProperties: {}', archiveIncludeProperties)

            // Check if we want to archive the item
            // TODO: Are we worried about hitting exclusion properties as well as inclusion properties
            //       on the same artifact? Does this need to be handled?
            if (archiveTiming && archiveExcludeProperties && archiveIncludeProperties) {
                def boolean keepArtifact = false

                // Check if we are supposed to leave any number of artifacts per directory
                if (numKeepArtifacts > 0) {
                    // Get the parent path
                    // NOTE: assuming one directory per unique type of artifact
                    def parentPath = artifact.getParent()
                    // Get the number of artifacts per the parent directory
                    def artifactsCount = repositories.getArtifactsCount(parentPath)
                    log.info('artifact parent path: {}, artifacts count in path: {}', parentPath, artifactsCount)

                    // Check if we need to skip aging the artifact
                    if (artifactsCount <= numKeepArtifacts) {
                        log.info('Skipping age process for artifact {} since due to the number of artifacts keep policy', artifact)
                        keepArtifact = true
                    }
                }

                // One last check to make sure we actually want to archive the artifact
                if (!keepArtifact) {
                    log.warn('About to archive artifact: {}', artifact)

                    // Get the properties from the existing artifact
                    Properties properties = repositories.getProperties(artifact)

                    // Deploy over the existing artifact with a 1-byte file
                    byte[] buf = new byte[1]
                    def status = repositories.deploy(artifact, new ByteArrayInputStream(buf))
                    log.debug('Status of deploy: {}', status)
                    if (status.statusCode != ArchiveConstants.HTTP_OK) {
                        log.error('Call to deploy artifact {} failed!', artifact)
                    }

                    // Add all of the properties back to the artifact
                    properties.keys().each { key ->
                        Set<String> values = properties.get(key)
                        log.debug('Adding key: {}, values: {} to re-deployed artifact', key, values)
                        repositories.setProperty(artifact, key, *(values as List))
                    }

                    // Call the function to move the artifact
                    moveBuildArtifact(archiveRepo, artifact, archiveProperty, todayTime)

                    artifactsArchived++
                }
            } else {
                log.info('Not archiving artifact: {}', artifact)
                log.debug('Timing archive policy status: {}', archiveTiming)
                log.debug('Exclude properties policy status: {}', archiveExcludeProperties)
                log.debug('Include properties policy status: {}', archiveIncludeProperties)
            }
        }

    log.warn('Process found {} total artifact(s)', artifactsCleanedUp.size)
    log.warn('Process archived {} total artifact(s)', artifactsArchived)
}

// Function to move the build artifact and set a property for the time it was moved
def moveBuildArtifact(archiveRepo, RepoPath artifact, String property, time) {
    // Get the translated file path for the new repo
    def translatedFilePath = repositories.translateFilePath(artifact, archiveRepo)
    log.debug('translatedFilePath: {}', translatedFilePath)

    // Get the path for the repo to move the artifact to
    def archiveRepoPath = RepoPathFactory.create(archiveRepo, translatedFilePath)
    log.debug('archiveRepoPath: {}', archiveRepoPath)

    // Move the actual artifact and check that it worked
    def status = repositories.move(artifact, archiveRepoPath)
    log.debug('status of move: {}', status)
    if (status.statusCode != ArchiveConstants.HTTP_OK) {
        log.error('Call to move artifact {} failed!', artifact)
    }

    // Tag the artifact as being archived (set a new property)
    def properties = repositories.setProperty(archiveRepoPath, property, String.valueOf(time))
    log.debug('Artifact {} properties: {}', archiveRepoPath, properties)
}

// Function to check if an artifact meets the archive timing policies
boolean checkArchiveTimingPolicies(
    artifact,
    lastModifiedDays,
    lastUpdatedDays,
    createdDays,
    lastDownloadedDays,
    ageDays,
    itemInfo,
    todayTime) {
    long compareDays

    // Check the last modified policy if it is set
    if (lastModifiedDays != 0) {
        def lastModifiedTime = new Date(itemInfo.getLastModified())
        log.debug('{} was last modified: {}', artifact, lastModifiedTime)

        // Calculate the days between today and the chosen policy time
        compareDays = getCompareDays(todayTime, lastModifiedTime.time)
        log.debug('{} days since last modified: {}', artifact, compareDays)

        // Check if the number of days meets the days specified to archive
        if (!checkTimingPolicy(compareDays, lastModifiedDays, artifact, 'last modified')) {
            return false
        }
    }
    // Check the last updated policy if it is set
    if (lastUpdatedDays != 0) {
        def lastUpdatedTime = new Date(itemInfo.getLastUpdated())
        log.debug('{} was last updated: {}', artifact, lastUpdatedTime)

        // Calculate the days between today and the chosen policy time
        compareDays = getCompareDays(todayTime, lastUpdatedTime.time)
        log.debug('{} days since last updated: {}', artifact, compareDays)

        // Check if the number of days meets the days specified to archive
        if (!checkTimingPolicy(compareDays, lastUpdatedDays, artifact, 'last updated')) {
            return false
        }
    }
    // Check the created policy if it is set
    if (createdDays != 0) {
        def createdTime = new Date(itemInfo.getCreated())
        log.debug('{} was created: {}', artifact, createdTime)

        // Calculate the days between today and the chosen policy time
        compareDays = getCompareDays(todayTime, createdTime.time)
        log.debug('{} days since created: {}', artifact, compareDays)

        // Check if the number of days meets the days specified to archive
        if (!checkTimingPolicy(compareDays, createdDays, artifact, 'created')) {
            return false
        }
    }
    // Check the last downloaded policy if it is set
    if (lastDownloadedDays != 0) {
        // Get the StatsInfo on the item
        def statsInfo = (StatsInfo) repositories.getStats(artifact)
        log.debug('Artifact {} stats info: {}', artifact, statsInfo)

        // Get the last downloaded date
        def lastDownloadedTime = new Date(statsInfo.getLastDownloaded())
        log.debug('{} was last downloaded: {}', artifact, lastDownloadedTime)

        // Calculate the days between today and the chosen policy time
        compareDays = getCompareDays(todayTime, lastDownloadedTime.time)
        log.debug('{} days since last downloaded: {}', artifact, compareDays)

        // Check if the number of days meets the days specified to archive
        if (!checkTimingPolicy(compareDays, lastDownloadedDays, artifact, 'last downloaded')) {
            return false
        }
    }
    // Check the age policy if it is set
    if (ageDays != 0) {
        // Get the FileInfo on the item
        def fileInfo = repositories.getFileInfo(artifact)
        log.debug('{} file info: {}', artifact, fileInfo)
        def compareTime = fileInfo.getAge()
        log.debug('{} age: {}', artifact, compareTime)
        compareDays = compareTime / ArchiveConstants.DAYS_TO_MILLIS
        log.debug('{} is {} days old', artifact, compareDays)

        // Check if the number of days meets the days specified to archive
        if (!checkTimingPolicy(compareDays, ageDays, artifact, 'age')) {
            return false
        }
    }

    // If we made it this far, we passed all of the timing policy checks
    log.debug('{} passed the timing policy check(s)', artifact)
    true
}

// Function to check the timing policy against what was specified in number of days
boolean checkTimingPolicy(compareDays, days, artifact, String policyName) {
    // Compare the days
    if (compareDays >= days) {
        log.debug('{} passed the {} policy check ({} days)', artifact, policyName, days)
        return true
    }

    log.debug('{} did not pass the {} policy check ({} days)', artifact, policyName, days)
    false
}

// Function to take in last policy time and get the number of days it correlates to
// so we can compare this to the number of days for our aging policy
int getCompareDays(todayTime, policyTime) {
    (todayTime - policyTime) / ArchiveConstants.DAYS_TO_MILLIS
}

// Function to take in a string representation of properties and return the map of it
Map<String, String> translatePropertiesString(String properties) {
    // Verify the properties string
    if (properties ==~ /(\w.+)(:\w.)*(;(\w.+)(:\w.)*)*/) {
        log.debug('Properties are of the proper format! Properties: {}', properties)
    } else {
        log.error('Properties are not of the proper format: {}. Exiting now!', properties)
        // Throw an exception due to the wrong input
        throw new CancelException('Incorrect format for properties!', 400)
    }

    // The map to be filled in
    Map<String, String> map = new HashMap()

    // Split the string by ';'
    String[] propertySets = properties.tokenize(';')

    // Iterate over the property sets
    propertySets.each {
        log.debug('propertySet: {}', it)
        // Split the key and value by ':'
        def (key, value) = it.tokenize(':')
        log.debug('key: {}, value: {}', key, value)
        // Add the set to the map
        map.put(key, value)
    }

    map
}

// Function to check an artifact against a property set
boolean verifyProperties(artifact, Map<String, String> propertyMap, boolean inclusive) {
    log.debug('verify properties called with inclusive: {}', inclusive)

    // Get the properties for the artifact
    Properties properties = repositories.getProperties(artifact)
    log.debug('Got properties for artifact: {}', properties)

    // Iterate over the propertySet we are verifying the artifact against
    for (String key : propertyMap.keySet()) {
        log.debug('iteration --> key: {}', key)

        // Check if the artifact has the property
        if (repositories.hasProperty(artifact, key)) {
            // Get the value we need to check for
            value = propertyMap.get(key)
            log.debug('value we are attempting to match: {}, for key: {}', value, key)

            // Check if we were even given a value to match on the key
            if (value != null) {
                // Check if the artifact contains the value for the key
                valueSet = repositories.getPropertyValues(artifact, key)
                log.debug('value set: {}, size: {}', valueSet, valueSet.size())
                if (valueSet.contains(value)) {
                    log.debug('Both have key: {}, value: {}', key, value)
                } else {
                    log.debug('Both have key: {}, but values differ. Value checked: {}', key, value)
                    return !inclusive
                }
            } else {
                log.debug('We were not given a value for the provided key: {}, this is a match since the key matches.', key)
            }
        } else {
            log.debug('The artifact did not contain the key: {}, failure to match properties', key)
            return !inclusive
        }
    }

    // Return true or false depending on include/exclude logic
    inclusive
}
