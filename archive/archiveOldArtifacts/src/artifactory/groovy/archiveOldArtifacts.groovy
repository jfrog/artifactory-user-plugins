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

import groovy.time.TimeCategory
import groovy.transform.ToString
import org.artifactory.exception.CancelException
import org.artifactory.fs.ItemInfo
import org.artifactory.fs.StatsInfo
import org.artifactory.model.xstream.fs.StatsImpl
import org.artifactory.repo.RepoPath
import org.artifactory.repo.RepoPathFactory

import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit

/**
 * Example REST call(s):
 * 1. Archive any artifact over 30 days old:
 *    curl -X POST -v -u <admin_user>:<admin_password> "http://localhost:8080/artifactory/api/plugins/execute/archive_old_artifacts?params=ageDays=30"
 * 2. Archive any artifact that is 30 days old and has the following properties set:
 *    curl -X POST -v -u <admin_user>:<admin_password> "http://localhost:8080/artifactory/api/plugins/execute/archive_old_artifacts?params=ageDays=30;includePropertySet=deleteme:true;junk:true"
 * 3. Archive any artifact that has not been downloaded in 60 days, excluding those with a certain property set:
 *    curl -X POST -v -u <admin_user>:<admin_password> "http://localhost:8080/artifactory/api/plugins/execute/archive_old_artifacts?params=lastDownloadedDays=60;excludePropertySet=keeper:true"
 * 4. Archive only *.tgz files that are 30 days old and have not been downloaded in 15 days:
 *    curl -X POST -v -u <admin_user>:<admin_password> "http://localhost:8080/artifactory/api/plugins/execute/archive_old_artifacts?params=filePattern=*.tgz;ageDays=30;lastDownloadedDays=15"
 * 5. Archive any *.tgz artifact that is 30 days old and is tagged with artifact.delete:
 *    curl -X POST -v -u <admin_user>:<admin_password> "http://localhost:8080/artifactory/api/plugins/execute/archive_old_artifacts?params=filePattern=*.tgz;ageDays=30;includePropertySet=artifact.delete"
 * 6. Archive any *.tgz artifact that is 15 days old and is tagged with artifact.delete=true:
 *    curl -X POST -v -u <admin_user>:<admin_password> "http://localhost:8080/artifactory/api/plugins/execute/archive_old_artifacts?params=filePattern=*.tgz;ageDays=15;includePropertySet=artifact.delete:true"
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
 * Available filters:
 * 1. pathPattern   the glob that the path of the artifact should match
 * 2. filePattern   the glob that the name of the artifact filename should match
 *
 * Archive actions:
 * 1. copyArtifactToDisk    true/false
 *                          if true, archived artifacts are downloaded to the local disk path
 *                          as configured with the environment variable ARTIFACTORY_DATA_ARCHIVE.
 *                           if false, archived artifacts are deleted!
 *
 * What is glob?
 * See https://docs.oracle.com/javase/tutorial/essential/io/fileOps.html#glob
 *
 * One can set any number of 'time period' archive policies as well as any number of include and exclude
 * attribute sets. It is up to the caller to decide how best to archive artifacts. If no archive policy
 * parameters are sent in, the plugin aborts in order to not allow default deleting of every artifact.
 *
 * The 'archive' process performs the following:
 * 1. Grabs all of the currently set properties on the artifact
 * 2. if copyArtifactToDisk is true; artifact is downloaded to the local disk path
 *    as configured with the environment variable ARTIFACTORY_DATA_ARCHIVE
 * 3. Does a deploy over top of the artifact with a small size file (to conserve space),
 *    explaining the file is archived, mentioning the archived location if copyArtifactToDisk is true
 * 4. Adds all of the previously held attributes to the newly deployed small size artifact
 * 5. Moves the artifact from the source repository to the destination repository specified
 * 6. Adds a property containing the archive timestamp to the artifact
 *
 * All Artifactory user plugin docs can be found at: https://www.jfrog.com/confluence/display/RTF/User+Plugins
 */

class Globals {
    static final String DATA_ARCHIVE_ENV_VAR_NAME = "ARTIFACTORY_DATA_ARCHIVE"
    static final String pathToArchiveBase = System.getenv(DATA_ARCHIVE_ENV_VAR_NAME)
    // tag::restApiDefaultParams[]
    static final Map<String, Object> DEFAULT_PARAMS = [
            pathPattern       : '**',
            filePattern       : '*',
            srcRepo           : '',
            archiveRepo       : '',
            lastModifiedDays  : 0,
            lastUpdatedDays   : 0,
            createdDays       : 0,
            lastDownloadedDays: 0,
            ageDays           : 0,
            excludePropertySet: '',
            includePropertySet: '',
            archiveProperty   : 'archived.timestamp',
            numKeepArtifacts  : 0,
            copyArtifactToDisk: false,
    ]
    // end::restApiDefaultParams[]
}

init()

executions {
    // tag::restApiDetails[]
    archive_old_artifacts(
            description: 'Archive old artifacts',
            version: '1.0',
            httpMethod: 'POST',
            params: Globals.DEFAULT_PARAMS,
    ) { params ->
        // end::restApiDetails[]
        def config = new ArchiveConfig(Globals.DEFAULT_PARAMS, params)
        archiveOldArtifacts(config)
    }
}

///**
// * A job definition.
// * The first value is a unique name for the job.
// * Job runs are controlled by the provided interval or cron expression, which are mutually exclusive.
// * The actual code to run as part of the job should be part of the job's closure.
// *
// * Parameters:
// * delay (long) - An initial delay in milliseconds before the job starts running (not applicable for a cron job).
// * interval (long) -  An interval in milliseconds between job runs.
// * cron (java.lang.String) - A valid cron expression used to schedule job runs (see: https://www.quartz-scheduler.org/documentation/quartz-2.3.0/tutorials/crontrigger)
// *                           Format: * * * * * * *
// *                           These sub-expression are separated with white-space, and represent:
// *                           1. Seconds
// *                           2. Minutes
// *                           3. Hours
// *                           4. Day-of-Month
// *                           5. Month; 0 and 11 (1-12 or JAN-DEC)
// *                           6. Day-of-Week; 1 and 7 (1-7 or SUN-SAT)
// *                           7. Year (optional field)
// */
//jobs {
//
//    /**
//     * Example of a cronjob that removes application releases after 365 days from Artifactory,
//     * but as a backup, writes the artifacts to the archive disk before removing them
//     */
//    archiveAppReleaseCandidates(cron: "0 0 9 ? * SAT") { // 09:00 every saturday
//        def params = [
//                pathPattern       : 'com/company/app/**',
//                filePattern       : '*',
//                srcRepo           : 'deploy-local',
//                archiveRepo       : 'deploy-local-archive',
//                createdDays       : 365,
//                excludePropertySet: 'archived.timestamp',
//                archiveProperty   : 'archived.timestamp',
//                copyArtifactToDisk: true,
//        ]
//        def config = new ArchiveConfig(Globals.DEFAULT_PARAMS, params)
//        archiveOldArtifacts(config)
//    }
//
//    /**
//     * Example of a cronjob that removed application releases after 14 days from Artifactory,
//     * and does not backup the artifacts to disk before archiving. They are really gone after cleanup!
//     */
//    removeAppTestReleases(cron: "0 0 21 ? * MON-FRI") { // 21:00 every monday til friday
//        def params = [
//                pathPattern       : 'com/company/app_test/**',
//                filePattern       : '*',
//                srcRepo           : 'deploy-local',
//                archiveRepo       : 'deploy-local-archive',
//                createdDays       : 14,
//                excludePropertySet: 'archived.timestamp',
//                archiveProperty   : 'archived.timestamp',
//                copyArtifactToDisk: false,
//        ]
//        def config = new ArchiveConfig(Globals.DEFAULT_PARAMS, params)
//        archiveOldArtifacts(config)
//    }
//}

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
public void archiveOldArtifacts(ArchiveConfig config) {
    log.info(logMsg(config, 'Starting archive process for old artifacts...'))
    logConfig(config)

    config.verify()
    if (config.copyArtifactToDisk) {
        def archiveStorage = new ArchiveStorageAvailability()
        if (!archiveStorage.isAvailable()) {
            def msg = logMsg(config, "Archive storage not available, because:\n" + archiveStorage.getErrors().join("\n"))
            throw new CancelException(msg, 412)
        }
    }
    int artifactsArchived = 0
    def startTime = new Date()
    def pathMatcher = FileSystems.default.getPathMatcher('glob:' + config.pathPattern)
    def artifactsCleanedUp = searches.artifactsByName(config.filePattern, config.srcRepo).each { RepoPath artifact ->
        if (!pathMatcher.matches(Paths.get(artifact.path))) {
            log.trace(logMsg(config, "Skipping artifact [{}] because path doesn't match pathPattern [{}]"),
                    artifact, config.pathPattern)
            return
        }
        log.trace(logMsg(config, 'Found artifact: {}'), artifact)
        long currentTime = new Date().time
        if (shouldArtifactBeArchived(artifact, config, currentTime)) {
            archiveArtifact(artifact, config, currentTime)
            artifactsArchived++
        } else {
            log.trace(logMsg(config, 'Not archiving artifact: {}'), artifact)
        }
    }
    def endTime = new Date()
    log.trace(logMsg(config, 'Process found {} artifact(s)'), artifactsCleanedUp.size)
    log.info(logMsg(config, 'Process archived {} artifact(s) in {}'), artifactsArchived, TimeCategory.minus(endTime, startTime))
}

private String logMsg(ArchiveConfig config, String msg) {
    return "[${config.correlationId}] ${msg}"
}

private boolean shouldArtifactBeArchived(RepoPath artifact, ArchiveConfig config, long currentTime) {
    ItemInfo itemInfo = repositories.getItemInfo(artifact)
    log.trace(logMsg(config, 'Artifact {} item info: {}'), artifact, itemInfo)
    boolean archiveTiming = shouldArtifactBeArchivedBasedOnTimingPolicies(artifact, config, itemInfo, currentTime)
    boolean archiveExcludeProperties = shouldArtifactBeArchivedBasedOnExcludePropertiesPolicies(artifact, config)
    boolean archiveIncludeProperties = shouldArtifactBeArchivedBasedOnIncludePropertiesPolicies(config, artifact)
    boolean archiveNumKeepCount = shouldArtifactBeArchivedBasedOnNumberOfArtifactsToKeepPolicies(config, artifact)
    log.trace(logMsg(config, 'Based on timing policies, should artifact {} be archived?: {}'), artifact, archiveTiming ? 'Yes' : ' No')
    log.trace(logMsg(config, 'Based on exclude properties policies, should artifact {} be archived?: {}'), artifact, archiveExcludeProperties ? 'Yes' : 'No')
    log.trace(logMsg(config, 'Based on include properties policies, should artifact {} be archived?: {}'), artifact, archiveIncludeProperties ? 'Yes' : 'No')
    log.trace(logMsg(config, 'Based on number of artifacts to keep policies, should artifact {} be archived?: {}'), artifact, archiveNumKeepCount ? 'Yes' : 'No')

    // TODO: Are we worried about hitting exclusion properties as well as inclusion properties
    //       on the same artifact? Does this need to be handled?
    if (archiveTiming &&
            archiveExcludeProperties &&
            archiveIncludeProperties &&
            archiveNumKeepCount
    ) {
        return true
    }
    return false
}

private boolean shouldArtifactBeArchivedBasedOnNumberOfArtifactsToKeepPolicies(ArchiveConfig config, RepoPath artifact) {
    if (config.numKeepArtifacts == 0) {
        return true
    }
    // NOTE: assuming one directory per unique type of artifact
    def parentPath = artifact.getParent()
    def artifactsCount = repositories.getArtifactsCount(parentPath)
    log.trace(logMsg(config, 'Found {} artifacts in artifact parent path [{}]'), artifactsCount, parentPath)
    if (artifactsCount > config.numKeepArtifacts) {
        return true
    }
    log.trace(logMsg(config, 'Skipping age process for artifact {} since due to the number of artifacts keep policy of {}'), artifact, config.numKeepArtifacts)
    return false
}

private boolean shouldArtifactBeArchivedBasedOnIncludePropertiesPolicies(ArchiveConfig config, RepoPath artifact) {
    if (config.includePropertySet == '') {
        return true
    }
    Map<String, String> includeMap = translatePropertiesString(config.includePropertySet)
    return shouldArtifactBeArchivedBasedOnProperties(artifact, includeMap, true)
}

private boolean shouldArtifactBeArchivedBasedOnExcludePropertiesPolicies(RepoPath artifact, ArchiveConfig config) {
    if (config.excludePropertySet == '') {
        return true
    }
    Map<String, String> excludeMap = translatePropertiesString(config.excludePropertySet)
    return shouldArtifactBeArchivedBasedOnProperties(artifact, excludeMap, false)
}

private boolean shouldDoTimingPolicyCheck(ArchiveConfig config) {
    return config.lastModifiedDays != 0 ||
            config.lastUpdatedDays != 0 ||
            config.createdDays != 0 ||
            config.lastDownloadedDays != 0 ||
            config.ageDays != 0
}

private void archiveArtifact(RepoPath artifact, ArchiveConfig config, long timeOfArchiving) {
    log.info(logMsg(config, 'Archiving artifact: {}'), artifact)
    def archive = new ArtifactArchive(artifact)
    try {
        def content = """\
                This artifact has been archived!
                """.stripIndent()
        if (config.copyArtifactToDisk) {
            downloadArtifactToArchive(config, archive)
            content = """\
                This artifact has been archived!
                Contact the Artifactory administrators if you really need this artifact.
                
                The artifact is archived to: ${archive.archivePath}
                """.stripIndent()
        }
        org.artifactory.md.Properties properties = repositories.getProperties(artifact)
        replaceArtifactWithContent(config, archive, properties, content)
        moveArtifactToArchiveRepo(archive, config, timeOfArchiving)
    } catch (Exception cause) {
        log.error(logMsg(config, 'Failed to archive artifact [{}]'), artifact.toPath(), cause)
    }
}

private void downloadArtifactToArchive(ArchiveConfig config, ArtifactArchive archive) {
    def parentPath = archive.archivePath.parent
    if (Files.notExists(parentPath)) {
        Files.createDirectories(parentPath)
    }

    def archivePath = archive.archivePath
    def archiveFile = archivePath.toFile()
    if (Files.notExists(archivePath)) {
        Files.createFile(archivePath)
    }

    repositories.getContent(archive.artifact).withCloseable { artifactContent ->
        def artifactPathString = archive.artifactPath
        log.trace(logMsg(config, 'Archiving artifact [{}] to [{}]...'), artifactPathString, archivePath)
        def startTime = new Date()
        archiveFile.withOutputStream { archiveStream ->
            artifactContent.getInputStream().eachByte { inputByte ->
                archiveStream.write(inputByte)
            }
        }
        def endTime = new Date()
        log.info(logMsg(config, 'Archived artifact [{} (size: {})] to [{} (size: {})] in [{}]'),
                artifactPathString, toHumanSize(artifactContent.size),
                archivePath, toHumanSize(archiveFile.size()),
                TimeCategory.minus(endTime, startTime))
        if (archiveFile.size() != artifactContent.size) {
            def msg = String.format(logMsg(config, "Archiving artifact [%s (size: %s)] to [%s (size: %s)] failed! Size mismatch!"),
                    artifactPathString, toHumanSize(artifactContent.size),
                    archivePath, toHumanSize(archiveFile.size())
            )
            throw new ArchiveException(msg)
        }
        repositories.setProperty(archive.artifact, 'archived.path', archive.archivePath.toString())
    }
}

private void replaceArtifactWithContent(ArchiveConfig config, ArtifactArchive archive, org.artifactory.md.Properties properties, String content) {
    def status = repositories.deploy(archive.artifact, new ByteArrayInputStream(content.getBytes('utf-8')))
    log.trace(logMsg(config, 'Replaced content of artifact {} with hint text of being archived'), archive.artifact)
    if (status.isError()) {
        log.error(logMsg(config, 'Replacing content of artifact {} failed!'), archive.artifact)
    }

    // Add all of the properties back to the artifact
    properties.keys().each { key ->
        Set<String> values = properties.get(key)
        repositories.setProperty(archive.artifact, key, *(values as List))
        log.trace(logMsg(config, 'Added property [{} -> {}] from original to re-deployed artifact {}'), key, values, archive.artifact)
    }
}

private void moveArtifactToArchiveRepo(ArtifactArchive archive, ArchiveConfig config, long time) {
    def translatedFilePath = repositories.translateFilePath(archive.artifact, config.archiveRepo)
    def archiveRepoPath = RepoPathFactory.create(config.archiveRepo, translatedFilePath)
    def status = repositories.move(archive.artifact, archiveRepoPath)
    log.trace(logMsg(config, 'Moved artifact {} to archive artifact {}'), archive.artifact, archiveRepoPath)
    if (status.isError()) {
        log.error(logMsg(config, 'Moving artifact {} to archive artifact {} failed!'), archive.artifact, archiveRepoPath)
    }
    def properties = repositories.setProperty(archiveRepoPath, config.archiveProperty, String.valueOf(time))
    log.trace(logMsg(config, 'Tag artifact {} being archived with properties: {}'), archiveRepoPath, properties)
}

private boolean shouldArtifactBeArchivedBasedOnTimingPolicies(RepoPath artifact, ArchiveConfig config, ItemInfo itemInfo, long todayTime) {
    if (!shouldDoTimingPolicyCheck(config)) {
        return true
    }
    log.trace(logMsg(config, 'We are going to perform a timing policies check...'))
    long compareDays

    // Check the last modified policy if it is set
    if (config.lastModifiedDays != 0) {
        def lastModifiedTime = new Date(itemInfo.getLastModified())
        log.trace(logMsg(config, '{} was last modified: {}'), artifact, lastModifiedTime)

        // Calculate the days between today and the chosen policy time
        compareDays = getCompareDays(todayTime, lastModifiedTime.time)
        log.trace(logMsg(config, '{} days since last modified: {}'), artifact, compareDays)

        // Check if the number of days meets the days specified to archive
        def policyName = 'last modified'
        def days = config.lastModifiedDays
        if (!checkTimingPolicy(compareDays, days, artifact, policyName)) {
            log.trace(logMsg(config, '{} did not pass the {} policy check ({} days)'), artifact, policyName, days)
            return false
        }
        log.trace(logMsg(config, '{} passed the {} policy check ({} days)'), artifact, policyName, days)
    }
    // Check the last updated policy if it is set
    if (config.lastUpdatedDays != 0) {
        def lastUpdatedTime = new Date(itemInfo.getLastUpdated())
        log.trace(logMsg(config, '{} was last updated: {}'), artifact, lastUpdatedTime)

        // Calculate the days between today and the chosen policy time
        compareDays = getCompareDays(todayTime, lastUpdatedTime.time)
        log.trace(logMsg(config, '{} days since last updated: {}'), artifact, compareDays)

        // Check if the number of days meets the days specified to archive
        def policyName = 'last updated'
        def days = config.lastUpdatedDays
        if (!checkTimingPolicy(compareDays, days, artifact, policyName)) {
            log.trace(logMsg(config, '{} did not pass the {} policy check ({} days)'), artifact, policyName, days)
            return false
        }
        log.trace(logMsg(config, '{} passed the {} policy check ({} days)'), artifact, policyName, days)
    }
    // Check the created policy if it is set
    if (config.createdDays != 0) {
        def createdTime = new Date(itemInfo.getCreated())
        log.trace(logMsg(config, '{} was created: {}'), artifact, createdTime)

        // Calculate the days between today and the chosen policy time
        compareDays = getCompareDays(todayTime, createdTime.time)
        log.trace(logMsg(config, '{} days since created: {}'), artifact, compareDays)

        // Check if the number of days meets the days specified to archive
        def policyName = 'created'
        def days = config.createdDays
        if (!checkTimingPolicy(compareDays, days, artifact, policyName)) {
            log.trace(logMsg(config, '{} did not pass the {} policy check ({} days)'), artifact, policyName, days)
            return false
        }
        log.trace(logMsg(config, '{} passed the {} policy check ({} days)'), artifact, policyName, days)
    }
    // Check the last downloaded policy if it is set
    if (config.lastDownloadedDays != 0) {
        // Get the StatsInfo on the item
        StatsInfo statsInfo = repositories.getStats(artifact)

        // If artifact is never downloaded
        if (statsInfo == null) {
            statsInfo = new StatsImpl()
            statsInfo.lastDownloaded = itemInfo.getCreated()
        }

        log.trace(logMsg(config, 'Artifact {} stats info: {}'), artifact, statsInfo)

        // Get the last downloaded date
        def lastDownloadedTime = new Date(statsInfo.getLastDownloaded())
        log.trace(logMsg(config, '{} was last downloaded: {}'), artifact, lastDownloadedTime)

        // Calculate the days between today and the chosen policy time
        compareDays = getCompareDays(todayTime, lastDownloadedTime.time)
        log.trace(logMsg(config, '{} days since last downloaded: {}'), artifact, compareDays)

        // Check if the number of days meets the days specified to archive
        def policyName = 'last downloaded'
        def days = config.lastDownloadedDays
        if (!checkTimingPolicy(compareDays, days, artifact, policyName)) {
            log.trace(logMsg(config, '{} did not pass the {} policy check ({} days)'), artifact, policyName, days)
            return false
        }
        log.debug(logMsg(config, '{} passed the {} policy check ({} days)'), artifact, policyName, days)
    }
    // Check the age policy if it is set
    if (config.ageDays != 0) {
        // Get the FileInfo on the item
        def fileInfo = repositories.getFileInfo(artifact)
        log.trace(logMsg(config, '{} file info: {}'), artifact, fileInfo)
        def compareTime = fileInfo.getAge()
        log.trace(logMsg(config, '{} age: {}'), artifact, compareTime)
        compareDays = compareTime / ArchiveConstants.DAYS_TO_MILLIS
        log.trace(logMsg(config, '{} is {} days old'), artifact, compareDays)

        // Check if the number of days meets the days specified to archive
        def policyName = 'age'
        def days = config.ageDays
        if (!checkTimingPolicy(compareDays, days, artifact, policyName)) {
            log.trace(logMsg(config, '{} did not pass the {} policy check ({} days)'), artifact, policyName, days)
            return false
        }
        log.trace(logMsg(config, '{} passed the {} policy check ({} days)'), artifact, policyName, days)
    }

    // If we made it this far, we passed all of the timing policy checks
    log.trace(logMsg(config, '{} passed the timing policy check(s)'), artifact)
    return true
}

// Function to check the timing policy against what was specified in number of days
private boolean checkTimingPolicy(long compareDays, int days, RepoPath artifact, String policyName) {
    // Compare the days
    if (compareDays >= days) {
        return true
    }
    return false
}

// Function to take in last policy time and get the number of days it correlates to
// so we can compare this to the number of days for our aging policy
private int getCompareDays(long todayTime, long policyTime) {
    return (todayTime - policyTime) / ArchiveConstants.DAYS_TO_MILLIS
}

private Map<String, String> translatePropertiesString(String properties) {
    if (!(properties ==~ /(\w.+)(:\w.)*(;(\w.+)(:\w.)*)*/)) {
        throw new CancelException("Incorrect format of properties: ${properties}. Exiting now!", 400)
    }
    Map<String, String> result = new HashMap()
    String[] propertySets = properties.tokenize(';')
    propertySets.each {
        def (key, value) = it.tokenize(':')
        result.put(key, value)
    }
    return result
}

private boolean shouldArtifactBeArchivedBasedOnProperties(RepoPath artifact, Map<String, String> propertyMap, boolean inclusive) {
    // TODO: Remove debugs, cleanup, and just test this method
    log.trace('verify properties called with inclusive: {}', inclusive)

    // Get the properties for the artifact
    org.artifactory.md.Properties properties = repositories.getProperties(artifact)
    log.trace('Got properties for artifact: {}', properties)

    // Iterate over the propertySet we are verifying the artifact against
    for (String key : propertyMap.keySet()) {
        log.trace('iteration --> key: {}', key)

        // Check if the artifact has the property
        if (repositories.hasProperty(artifact, key)) {
            // Get the value we need to check for
            def value = propertyMap.get(key)
            log.trace('value we are attempting to match: {}, for key: {}', value, key)

            // Check if we were even given a value to match on the key
            if (value != null) {
                // Check if the artifact contains the value for the key
                def valueSet = repositories.getPropertyValues(artifact, key)
                log.trace('value set: {}, size: {}', valueSet, valueSet.size())
                if (valueSet.contains(value)) {
                    log.trace('Both have key: {}, value: {}', key, value)
                } else {
                    log.trace('Both have key: {}, but values differ. Value checked: {}', key, value)
                    return !inclusive
                }
            } else {
                log.trace('We were not given a value for the provided key: {}, this is a match since the key matches.', key)
            }
        } else {
            log.trace('The artifact did not contain the key: {}, failure to match properties', key)
            return !inclusive
        }
    }
    return inclusive
}

private String toHumanSize(long bytes) {
    long base = 1024L
    int decimals = 3
    def postfix = [' bytes', ' KB', ' MB', ' GB', ' TB', ' PB']
    int i = Math.log(bytes) / Math.log(base) as int
    i = (i >= postfix.size() ? postfix.size() - 1 : i)
    try {
        return Math.round((bytes / base**i) * 10**decimals) / 10**decimals + postfix[i]
    } catch (Exception cause) {
        return bytes + postfix[0]
    }
}

private void init() {
    log.warn("""\
        If you don't see any more informative logging from this plugin,\n
        the log level of this plugin is set to 'warn', Artifactory' default log level for plugins.\n
        To change the plugin log level,
        add the following to `\${ARTIFACTORY_HOME}/etc/logback.xml`:
            <logger name="archiveOldArtifacts">
              <level value="info"/>
            </logger>
        Atleast 'info' log level is recommended.
        """.stripIndent())
    log.info("info log level enabled")
    log.debug("debug log level enabled")
    log.trace("trace log level enabled")

    def archiveStorage = new ArchiveStorageAvailability()
    if (!archiveStorage.isPathConfigured()) {
        log.warn("Archive storage not available, as environment variable [{}] is not set. The plugin still works, but without support for archiving to disk", Globals.DATA_ARCHIVE_ENV_VAR_NAME)
    } else if (!archiveStorage.isAvailable()) {
        def msg = "Plugin configured with Archive storage, but failing with the following error(s):\n" + archiveStorage.getErrors().join("\n")
        throw new CancelException(msg, 412)
    }
}

class ArchiveStorageAvailability {
    final boolean pathConfigured
    final boolean pathExisting
    final boolean directory
    final boolean readable
    final boolean writable

    ArchiveStorageAvailability() {
        pathConfigured = Globals.pathToArchiveBase ? true : false
        def archiveBasePath = Paths.get(Globals.pathToArchiveBase)
        pathExisting = Files.exists(archiveBasePath)
        directory = Files.isDirectory(archiveBasePath)
        readable = Files.isReadable(archiveBasePath)
        writable = Files.isWritable(archiveBasePath)
    }

    boolean isAvailable() {
        return pathConfigured && pathExisting && directory && readable && writable
    }

    List<String> getErrors() {
        def errors = []
        if (!pathConfigured) {
            errors << "Archive storage not available, as environment variable [${Globals.DATA_ARCHIVE_ENV_VAR_NAME}] is not set. The plugin still works, but without support for archiving to disk"
        }
        def baseMsg = "Archive storage available at path [${Globals.pathToArchiveBase}], as configured in the environment variable [Globals.DATA_ARCHIVE_ENV_VAR_NAME], "
        if (!pathExisting) {
            errors << "${baseMsg} but path does not exist!"
        }
        if (!directory) {
            errors << "${baseMsg} but path is not a directory!"
        }
        if (!readable) {
            errors << "${baseMsg} but path is not readable!"
        }
        if (!writable) {
            errors << "${baseMsg} but path is not writable!"
        }
        return errors
    }
}

private void logConfig(ArchiveConfig config) {
    log.info(logMsg(config, 'Path match pattern: {}'), config.pathPattern)
    log.info(logMsg(config, 'File match pattern: {}'), config.filePattern)
    log.info(logMsg(config, 'Source repository: {}'), config.srcRepo)
    log.info(logMsg(config, 'Archive repository: {}'), config.archiveRepo)
    log.info(logMsg(config, 'Archive after last modified days: {}'), config.lastModifiedDays)
    log.info(logMsg(config, 'Archive after last updated days: {}'), config.lastUpdatedDays)
    log.info(logMsg(config, 'Archive after created days: {}'), config.createdDays)
    log.info(logMsg(config, 'Archive after last downloaded days: {}'), config.lastDownloadedDays)
    log.info(logMsg(config, 'Archive after age days: {}'), config.ageDays)
    log.info(logMsg(config, 'Exclude property set: {}'), config.excludePropertySet)
    log.info(logMsg(config, 'Include property set: {}'), config.includePropertySet)
    log.info(logMsg(config, 'Archive property: {}'), config.archiveProperty)
    log.info(logMsg(config, 'Number of artifacts to keep per directory: {}'), config.numKeepArtifacts)
    log.info(logMsg(config, 'Copy archived artifacts to disk: {}'), config.copyArtifactToDisk ? 'Yes' : 'No')
}

class ArtifactArchive {
    private final RepoPath artifact

    ArtifactArchive(RepoPath artifact) {
        this.artifact = artifact
    }

    RepoPath getArtifact() {
        return artifact
    }

    Path getArtifactPath() {
        return Paths.get(artifact.toPath())
    }

    Path getArchivePath() {
        return Paths.get(Globals.pathToArchiveBase, getArtifactPath().toString())
    }
}

class ArchiveException extends RuntimeException {
    ArchiveException(String message) {
        super(message)
    }
}

class ArchiveConstants {
    final static DAYS_TO_MILLIS = TimeUnit.MILLISECONDS.convert(1, TimeUnit.DAYS)
}

@ToString(includeNames = true)
class ArchiveConfig {
    String pathPattern
    String filePattern
    String srcRepo
    String archiveRepo
    int lastModifiedDays
    int lastUpdatedDays
    int createdDays
    int lastDownloadedDays
    int ageDays
    String excludePropertySet
    String includePropertySet
    String archiveProperty
    int numKeepArtifacts
    boolean copyArtifactToDisk
    final String correlationId

    ArchiveConfig(Map<String, Object> defaults, Map<String, Object> params) {
        correlationId = UUID.randomUUID().toString()
        // Set properties based on defaults and provided params
        this.metaClass.properties.findAll { it.name != 'class' && it.name != 'correlationId' }.each {
            def key = it.name
            def value = params.getOrDefault(it.name, defaults.get(it.name))
            if (value instanceof List) {
                value = value.first()
            }
            value = value.asType(it.type)
            if (it.type == Boolean && value instanceof String) {
                if (value.toLowerCase() == 'false') {
                    value = false
                }
            }
            this."${key}" = value
        }
    }

    void verify() {
        if (lastModifiedDays == 0 &&
                lastUpdatedDays == 0 &&
                createdDays == 0 &&
                lastDownloadedDays == 0 &&
                ageDays == 0 &&
                excludePropertySet == '' &&
                includePropertySet == '') {
            def msg = '''\
                    No selection criteria specified!
                    Atleast one of the following parameters must be provided:
                        lastModifiedDays, lastUpdatedDays, createdDays,
                        lastDownloadedDays, ageDays, excludePropertySet, includePropertySet
                    '''.stripIndent()
            throw new CancelException(msg, 400)
        }

        if (archiveRepo == '' || srcRepo == '') {
            def errmsg = "Both [srcRepo] and [archiveRepo] must be defined [srcRepo: ${srcRepo}, archiveRepo: ${archiveRepo}]"
            throw new CancelException(errmsg, 400)
        }
    }
}
