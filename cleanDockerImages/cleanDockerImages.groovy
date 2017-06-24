/*
 * Copyright (C) 2015 JFrog Ltd.
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
 *
 * Created by Madhu Reddy on 6/16/17.
 */


import org.artifactory.fs.ItemInfo
import org.artifactory.repo.RepoPath

import java.util.concurrent.TimeUnit

import static org.artifactory.repo.RepoPathFactory.create

//This is executed via REST API
// usage: curl -X POST http://localhost:8088/artifactory/api/plugins/execute/cleanDockerImages

// Add your Artifactory local docker repos in the array below
String[] dockerRepos = ["docker-local-repo"]
maxDaysProperty = "docker.label.com.jfrog.artifactory.retention.maxDays"
maxCountProperty = "docker.label.com.jfrog.artifactory.retention.maxCount"

executions{
    cleanDockerImages() {

        //Reset Global Variables
        foundManifest=false
        listEndReached=false
        imagesCount = new TreeMap<String, Integer>()
        imagesPathMap = new HashMap<String, List<RepoPath>>()

        dockerRepos.each {
            log.debug "Call Delete Docker Images: $it"
            buildParentRepoPaths create(it)
        }
        message = '{"status":"okay"}'
        status = 200
    }

}


def private void buildParentRepoPaths(RepoPath artifactoryRepoPath){

    def List<RepoPath> grandParentReposList = new ArrayList()
    simpleTraverse(repositories.getItemInfo(artifactoryRepoPath), grandParentReposList)
    removeImagesMaxCount(imagesPathMap)

}


def private void removeImagesMaxCount(Map repoMap){

    Iterator repoIterator = repoMap.keySet().iterator()
    while(repoIterator.hasNext()){
        def String key = repoIterator.next()
        def ArrayList repoList = repoMap.get(key)
        def int maxImagesCount = imagesCount.get(key)

        if(maxImagesCount > 0){


            log.debug " ARRAY LIST  Size is " + repoList.size() + " AND MAX COUNT IS " + maxImagesCount

            if(repoList.size() > maxImagesCount){

                // If number of current docker images are more than maxcount, delete them
                for(int i=0;i<repoList.size()-maxImagesCount;i++){
                    RepoPath r = repoList.get(i)
                    log.debug "HASH MAP KEY = " + key + " AND VALUE = " + r.getId()

                    //Logic to delete docker images based on Max Count policy
                    //deleteDockerImage(RepoPath)

                }
            }

        }

    }

}

// For debug only
def private void printImagesMaxCount(Map repoMap){

    Iterator repoIterator = repoMap.keySet().iterator()

    while(repoIterator.hasNext()){
        def String key = repoIterator.next()
        def Integer value = repoMap.get(key)
        log.debug " HASH MAP KEY = " + key + " and VALUE = " + value

    }

}


/*
 * Traverse through the docker repo(directories and sub-directories) and
 * 1) delete the images immediately if the maxDays policy applies
 * 2) Aggregate the images that qualify for maxCount policy (to get deleted in the execution closure)
 *
 */
def private void simpleTraverse(ItemInfo parentInfo, List revistFoldersList){

    def RepoPath parentRepoPath = parentInfo.getRepoPath()
    if(foundManifest){
        if(listEndReached){
            //reset Skip when the List ends
            foundManifest=false
        }
        //Commenting Skipping logic
        // log.debug "Skipping to dig folder " + parentRepoPath.getName()
        //skipOtherDockerReposAfterManifestFound(parentRepoPath)
        //return
    }
    List<ItemInfo> childItems = repositories.getChildren(parentRepoPath)


    if (!childItems.isEmpty()) {

        def listSize = childItems.size()
        listEndReached=false
        for (int i=0;i<listSize;i++){

            if(i==(listSize-1)){
                listEndReached = true
            }
            def ItemInfo childItem = childItems.get(i);
            def RepoPath currentPath = childItem.getRepoPath()

            if (childItem.isFolder()) {
                simpleTraverse(childItem, revistFoldersList)

            }
            else{
                log.debug "Scanning File: " + currentPath.getName()
                if(isThisManifestFile(currentPath)){
                    foundManifest=true
                    //Get the properties here and delete based on policies
                    //Implement DaysPassed Policy First and delete the images that qualify
                    // And then aggregate the images info to group by image and sorted by create date for MaxCount policy
                    if(checkDaysPassedForDelete(childItem)){
                        log.debug("***** DELETE THE DOCKER IMAGE AS DAYS PASSED POLICY APPLIES " + parentRepoPath.getName())
                        //Implement days passed policy and delete any docker images(tags) first
                        deleteDockerImage(parentRepoPath)
                    } else{

                        def int maxCount = getMaxCountForDelete(childItem)
                        // if maxCount property is zero then don't delete any!!
                        if(maxCount >0){
                            log.debug "Adding to IMAGES MAP :" + parentRepoPath
                            def long parentCreatedDate = parentInfo.getCreated()
                            def String parentId = parentRepoPath.getParent().getId()
                            imagesCount.put(parentId, new Integer(maxCount))

                            if(!imagesPathMap.containsKey(parentId)){
                                log.debug "RESETTING ARRAY LIST :"
                                revistFoldersList = new ArrayList()
                                revistFoldersList.add(parentRepoPath)
                                imagesPathMap.put(parentId,revistFoldersList)

                            }else{
                                revistFoldersList = imagesPathMap.get(parentId)
                                revistFoldersList.add(parentRepoPath)
                                imagesPathMap.put(parentId,revistFoldersList)
                            }

                        }

                    }
                    // Once manifest.json file is found in a folder ignore other SHA files
                    skipOtherFilesAfterManifestFound(parentRepoPath)
                    break
                }

            }

        }
        // Handle the case if there is only subfolder in a folder to reset the skip mode (foundManifest=false)
        if(listSize==1){
            //reset skip mode when there is only one docker repo as we are breaking out of the for loop. End of the list reached!
            foundManifest=false
        }
        log.debug "********** Exiting the FOR LOOP ********** with manifest " + foundManifest


    }


}


def deleteDockerImage(RepoPath repPath){

    log.debug "@@@@@@@@@@ DELETING THE DOCKER IMAGE " + repPath + " @@@@@@@@@@@"
    //Remove comment below to delete the images
    //repositories.delete(repPath)

}

def skipOtherFilesAfterManifestFound(RepoPath repPath){

    /**
     * Write a custom code after manifest file is found during the traverse.
     * The repPath is the path of the Docker image tag where manifest file is found
     */
}

def skipOtherDockerReposAfterManifestFound(RepoPath repPath){

    /**
     * Write a custom code after manifest file is found during the traverse.
     * The repPath is the path of the Docker image tag where manifest file is found
     */
}


def private boolean isThisManifestFile(RepoPath repPath){

    def isManifestFile = false
    if (repPath.getName().equals("manifest.json")){
        isManifestFile = true
        foundManifest=true
    }
    return isManifestFile
}

/*
 * This method checks if the docker image's manifest has the property "com.jfrog.artifactory.retention.maxDays" for purge
 *
 */

def private boolean checkDaysPassedForDelete(ItemInfo manifestItem ){

    boolean daysPassed = false
    if(manifestItem){
        createDateOfDockerImage = manifestItem.getCreated()
        def policyToKeepDockerImageDays = repositories.getProperty(manifestItem.getRepoPath(),maxDaysProperty)
        if(policyToKeepDockerImageDays){
            log.debug "PROPERTY " + maxDaysProperty + " FOUND = " + policyToKeepDockerImageDays + " IN MANIFEST FILE"
            def policyIntDays = policyToKeepDockerImageDays.isInteger() ? policyToKeepDockerImageDays.toInteger() : null
            if(policyIntDays){
                daysInBetween = (new Date().getTime() - createDateOfDockerImage) / TimeUnit.MILLISECONDS.convert(1, TimeUnit.DAYS)
                if(daysInBetween >= policyToKeepDockerImageDays){
                    daysPassed = true
                }
            }
        } else{

        }
    }

    return daysPassed
}


/*
 * This method checks if the docker image's manifest has the property "com.jfrog.artifactory.retention.maxCount" for purge
 *
 */
def private int getMaxCountForDelete(ItemInfo manifestItem ){

    int maxCount = 0
    if(manifestItem){

        def policyImageMaxCountString = repositories.getProperty(manifestItem.getRepoPath(),maxCountProperty)
        if(policyImageMaxCountString){
            log.debug "PROPERTY " + maxCountProperty + " FOUND = " + policyImageMaxCountString + " IN MANIFEST FILE"
            def policyImageMaxCount = policyImageMaxCountString.isInteger() ? policyImageMaxCountString.toInteger() : null
            if(policyImageMaxCount > 0)
                maxCount = policyImageMaxCount
        }
    }

    return maxCount
}








