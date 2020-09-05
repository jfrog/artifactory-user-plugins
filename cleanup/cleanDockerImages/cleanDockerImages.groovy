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

// Created by Madhu Reddy on 6/16/17.

import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import groovy.transform.Field
import java.util.concurrent.TimeUnit
import org.artifactory.repo.RepoPathFactory
import org.artifactory.exception.CancelException

@Field final String CONFIG_FILE_PATH = "plugins/${this.class.name}.json"

// usage: curl -X POST http://localhost:8088/artifactory/api/plugins/execute/cleanDockerImages

executions {
    cleanDockerImages() { params ->
        def deleted = []
        def etcdir = ctx.artifactoryHome.etcDir
        def configFile = new File(ctx.artifactoryHome.etcDir, CONFIG_FILE_PATH)
        def config = new JsonSlurper().parse(configFile.toURL())

        def repos = config.repos ? config.repos : []
        def timeUnit = config.timeUnit ? config.timeUnit : "day"
        def timeInterval = config.timeInterval ? config.timeInterval : 1
        def dryRun = config.dryRun ? config.dryRun : false

        def calendarUntil = Calendar.getInstance()
        calendarUntil.add(mapTimeUnitToCalendar(timeUnit), -timeInterval)
        def maxUnusedSecondsAllowed = new Date().time - calendarUntil.getTime().getTime()
        log.info("maxUnusedSecondsAllowed: $maxUnusedSecondsAllowed")
        repos.each {
            log.debug("Cleaning Docker images in repo: $it")
            def del = buildParentRepoPaths(RepoPathFactory.create(it), maxUnusedSecondsAllowed, dryRun)
            deleted.addAll(del)
        }
        def json = [status: 'okay', dryRun: dryRun, deleted: deleted]
        message = new JsonBuilder(json).toPrettyString()
        status = 200
    }
}

def buildParentRepoPaths(path, maxUnusedSecondsAllowed, dryRun) {
    def deleted = [], oldSet = []
    def parentInfo = repositories.getItemInfo(path)
    simpleTraverse(parentInfo, oldSet, maxUnusedSecondsAllowed)
    for (img in oldSet) {
        deleted << img.id
        if (!dryRun) log.info("delete $img")//repositories.delete(img)
    }
    return deleted
}

// Traverse through the docker repo (directories and sub-directories) and:
// - delete the images immediately if the maxDays policy applies
// - Aggregate the images that qualify for maxCount policy (to get deleted in
//   the execution closure)
def simpleTraverse(parentInfo, oldSet, maxUnusedSecondsAllowed) {
    def parentRepoPath = parentInfo.repoPath
    def latestImageItemInfo = null
    def toBeDeletedImageTagsInCurrentRepo = []
    for (childItem in repositories.getChildren(parentRepoPath)) {
        // log.debug("CHILDITEM GETNAME: $childItem.getName()")
        def currentPath = childItem.repoPath
        if (childItem.isFolder()) {
            simpleTraverse(childItem, oldSet, maxUnusedSecondsAllowed)
            continue
        }
        // log.debug("Scanning File: $currentPath.name")
        if (currentPath.name != "manifest.json") continue

        if (parentRepoPath.name == "latest") {
            latestImageItemInfo = parentInfo
            continue
        }
        // get the properties here and delete based on policies:
        // - implement daysPassed policy first and delete the images that
        //   qualify
        // - aggregate the image info to group by image and sort by create
        //   date for maxCount policy
        if (checkDaysPassedForDelete(childItem, maxUnusedSecondsAllowed)) {
            log.debug("Adding to OLD MAP: $parentRepoPath")
            oldSet << parentRepoPath
            toBeDeletedImageTagsInCurrentRepo << parentRepoPath.name
        }
        break
    }

    if (latestImageItemInfo != null) {
        def shouldDeleteLatest = true
        log.info(toBeDeletedImageTagsInCurrentRepo.join(","))
        log.info((repositories.getChildren(parentRepoPath)*.name).join(","))
        for(childItem in repositories.getChildren(parentRepoPath)) {
            if (!toBeDeletedImageTagsInCurrentRepo.contains(childItem.name)) {
                shouldDeleteLatest = false
                break
            }
        }
        if (shouldDeleteLatest) {
            oldSet << latestImageItemInfo
        }
    }

}

def checkDaysPassedForDelete(item, maxUnusedSecondsAllowed) {

    def stats = repositories.getStats(item.repoPath)
    def itemInfo = repositories.getItemInfo(item.repoPath)
    def lastDownloaded = stats == null ? 0 : stats.getLastDownloaded()
    def lastModified = itemInfo.getLastModified()
    log.info("childItem.repoPath: ${item.repoPath.getName()}")
    log.info("lastDownloaded: $lastDownloaded")
    log.info("lastModified: $lastModified")
    log.info("new Date().time: ${new Date().time}")

    def lastUsed = lastDownloaded > lastModified ? lastDownloaded : lastModified
    return (new Date().time - lastUsed) >= maxUnusedSecondsAllowed
}

private def mapTimeUnitToCalendar (String timeUnit) {
    switch ( timeUnit ) {
        case "minute":
            return Calendar.MINUTE
        case "hour":
            return Calendar.HOUR
        case "day":
            return Calendar.DAY_OF_YEAR
        case "month":
            return Calendar.MONTH
        case "year":
            return Calendar.YEAR
        default:
            def errorMessage = "$timeUnit is no valid time unit. Please check your request or scheduled policy."
            log.error errorMessage
            throw new CancelException(errorMessage, 400)
    }
}
