/*
 * Copyright (C) 2020 JFrog Ltd.
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

// Created by Mohammad Ali Toufighi on 9/5/2020.

import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import groovy.transform.Field
import java.text.SimpleDateFormat
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
        def timeUnit = config.timeUnit ? config.timeUnit : "month"
        def timeInterval = config.timeInterval ? config.timeInterval : 1
        def dryRun = config.dryRun ? config.dryRun : false

        def calendarUntil = Calendar.getInstance()
        calendarUntil.add(mapTimeUnitToCalendar(timeUnit), -timeInterval)
        def maxUnusedSecondsAllowed = new Date().time - calendarUntil.getTime().getTime()
        repos.each {
            log.info("Cleaning older than $timeInterval $timeUnit(s) unused Docker images in repo: $it")
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
        if (!dryRun) {
            repositories.delete(img)
            sleep(200)
        }
        log.debug("Deleted $img.id")
    }
    return deleted
}

def simpleTraverse(parentInfo, oldSet, maxUnusedSecondsAllowed) {
    def parentRepoPath = parentInfo.repoPath
    def latestImageItemInfo = null
    def toBeDeletedImageTagsInCurrentRepo = []
    for (childItem in repositories.getChildren(parentRepoPath)) {
        def currentPath = childItem.repoPath

        if (currentPath.name == "latest"
            && childItem.isFolder()
            && hasManifestJsonInChildren(currentPath)) {
            latestImageItemInfo = childItem
            continue
        }

        if (childItem.isFolder()) {
            toBeDeletedImageTagsInCurrentRepo.addAll(simpleTraverse(childItem, oldSet, maxUnusedSecondsAllowed))
            continue
        }
        if (currentPath.name != "manifest.json") continue

        if (checkDaysPassedForDelete(childItem, maxUnusedSecondsAllowed)) {
            oldSet << parentRepoPath
            toBeDeletedImageTagsInCurrentRepo.add(parentRepoPath.name)
        }
        break
    }

    if (latestImageItemInfo != null) {
        if (toBeDeletedImageTagsInCurrentRepo.size() > 0
            && toBeDeletedImageTagsInCurrentRepo.size() == repositories.getChildren(parentRepoPath).size() - 1
            && !toBeDeletedImageTagsInCurrentRepo.contains("latest")) { // tof!
            // oldSet << latestImageItemInfo.repoPath
            // oldSet << latestImageItemInfo.repoPath.parent // remove the whole folder
        }
    }

    return toBeDeletedImageTagsInCurrentRepo

}

def hasManifestJsonInChildren(repoPath) {
    for (childItemInfo in repositories.getChildren(repoPath)) {
        if (!childItemInfo.isFolder() && childItemInfo.repoPath.name == "manifest.json")
            return true
    }
    return false
}

def checkDaysPassedForDelete(item, maxUnusedSecondsAllowed) {

    def stats = repositories.getStats(item.repoPath)
    def itemInfo = repositories.getItemInfo(item.repoPath)
    def lastDownloaded = stats == null ? 0 : stats.getLastDownloaded()
    def lastModified = itemInfo.getLastModified()
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
