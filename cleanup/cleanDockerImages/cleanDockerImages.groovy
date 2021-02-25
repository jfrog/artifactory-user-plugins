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
// Modified by Ruben Castrelo on 25/02/2021
import groovy.json.JsonBuilder
import java.util.concurrent.TimeUnit
import org.artifactory.repo.RepoPathFactory
String repoGlobal = "politicas-docker" // Virtual Repository for clean docker images
String cron = "0 * * ? * *" // only works in quartz date
boolean dryRunCron = true


// usage: curl -X POST http://localhost:8088/artifactory/api/plugins/execute/cleanDockerImages

executions {
    cleanDockerImages() { params ->
        def deleted = []   
        def dryRun = params['dryRun'] ? params['dryRun'][0] as boolean : false
        def repos = []
        def aqlRepo = "items.find({\"repo\":\""+repoGlobal+"\"})"
        searches.aql(aqlRepo.toString()) {
            for (item in it) {
                if (repos.isEmpty()) {
                    repos.add(item.repo)
                }
                if( repos.last() == item.repo) { 
                }else{ 
                    repos.add(item.repo)
                }
            }
        }
        repos.each {
            log.debug("Cleaning Docker images in repo: $it")
            def del = buildParentRepoPaths(RepoPathFactory.create(it), dryRun)
            deleted.addAll(del)
        }
        def json = [status: 'okay', dryRun: dryRun, deleted: deleted]
        message = new JsonBuilder(json).toPrettyString()
        status = 200
    }
}

// Schedule for run cleanDockerImages

jobs {
    "scheduledCleanDockerImages_1"(cron: cron) {
        log.info "Policy settings for scheduled run at($cron) for docker images, virtual repo ($repoGlobal)"
        def deleted = []   
        
        def repos = []
        def aqlRepo = "items.find({\"repo\":\""+repoGlobal+"\"})"
        searches.aql(aqlRepo.toString()) {
            for (item in it) {
                if (repos.isEmpty()) {
                    repos.add(item.repo)
                }
                if( repos.last() == item.repo) { 
                }else{ 
                    repos.add(item.repo)
                }
            }
        }
        repos.each {
            log.debug("Cleaning Docker images in repo: $it")
            def del = buildParentRepoPaths(RepoPathFactory.create(it), dryRunCron)
            deleted.addAll(del)
        }
        def json = [status: 'okay', dryRun: dryRunCron, deleted: deleted]
        message = new JsonBuilder(json).toPrettyString()
        log.info "Result $message"

    }
}



def buildParentRepoPaths(path, dryRun) {
    def deleted = [], oldSet = [], imagesPathMap = [:], imagesCount = [:]
    def parentInfo = repositories.getItemInfo(path)
    simpleTraverse(parentInfo, oldSet, imagesPathMap, imagesCount)
    for (img in oldSet) {
        deleted << img.id
        if (!dryRun) repositories.delete(img)
    }
    for (key in imagesPathMap.keySet()) {
        def repoList = imagesPathMap[key]
        def maxImagesCount = imagesCount[key]
        // If number of current docker images is more than maxcount, delete them
        if (maxImagesCount <= 0 || repoList.size() <= maxImagesCount) continue
        repoList = repoList.sort { it[1] }
        def deleteCount = repoList.size() - maxImagesCount
        for (i = 0; i < deleteCount; i += 1) {
            deleted << repoList[i][0].id
            if (!dryRun) repositories.delete(repoList[i][0])
        }
    }
    return deleted
}

// Traverse through the docker repo (directories and sub-directories) and:
// - delete the images immediately if the maxDays policy applies
// - Aggregate the images that qualify for maxCount policy (to get deleted in
//   the execution closure)
def simpleTraverse(parentInfo, oldSet, imagesPathMap, imagesCount) {
    def maxCount = null
    def parentRepoPath = parentInfo.repoPath
    for (childItem in repositories.getChildren(parentRepoPath)) {
        log.debug "childItem es $childItem"
        def currentPath = childItem.repoPath
        if (childItem.isFolder()) {
            simpleTraverse(childItem, oldSet, imagesPathMap, imagesCount)
            continue
        }
        log.debug("Scanning File: $currentPath.name")
        if (currentPath.name != "manifest.json") continue
        // get the properties here and delete based on policies:
        // - implement daysPassed policy first and delete the images that
        //   qualify
        // - aggregate the image info to group by image and sort by create
        //   date for maxCount policy
        if (checkDaysPassedForDelete(childItem)) {
            log.debug("Adding to OLD MAP: $parentRepoPath")
            oldSet << parentRepoPath
        } else if ((maxCount = getMaxCountForDelete(childItem)) > 0) {
            log.debug("Adding to IMAGES MAP: $parentRepoPath")
            def parentCreatedDate = parentInfo.created
            def parentId = parentRepoPath.parent.id
            def oldmax = maxCount
            if (parentId in imagesCount) oldmax = imagesCount[parentId]
            imagesCount[parentId] = maxCount > oldmax ? maxCount : oldmax
            if (!imagesPathMap.containsKey(parentId)) {
                imagesPathMap[parentId] = []
            }
            imagesPathMap[parentId] << [parentRepoPath, childItem.created]
        }
        break
    }
}

// This method checks if the docker image's manifest has the property
// "com.jfrog.artifactory.retention.maxDays" for purge
def checkDaysPassedForDelete(item) {
    def maxDaysProp = "com.jfrog.artifactory.retention.maxDays"
    def oneday = TimeUnit.MILLISECONDS.convert(1, TimeUnit.DAYS)
    def prop = repositories.getProperty(item.repoPath, maxDaysProp)
    if (!prop) return false
    log.debug("PROPERTY $maxDaysProp FOUND = $prop IN MANIFEST FILE")
    prop = prop.isInteger() ? prop.toInteger() : null
    if (prop == null) return false
    return ((new Date().time - item.created) / oneday) >= prop
    //return 0 // numero maximo de dias
}

// This method checks if the docker image's manifest has the property
//  for purge
def getMaxCountForDelete(item) {
    return 3 // maximun number of docker images
}