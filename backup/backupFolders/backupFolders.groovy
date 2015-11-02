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
 */

import groovy.json.JsonSlurper
import org.artifactory.exception.CancelException
import org.artifactory.fs.ItemInfo
import org.artifactory.repo.RepoPath
import org.artifactory.repo.RepoPathFactory
import org.artifactory.resource.ResourceStreamHandle

/**
 * Created by Alexei on 8/15/15.
 */

/**
 * The command to trigger the execution:
 * curl -X POST -uadmin:password "http://localhost:8081/artifactory/api/plugins/execute/backup" -T properties.json
 */
executions {
    backup { params, ResourceStreamHandle body ->
        assert body
        def json = new JsonSlurper().parse(new InputStreamReader(body.inputStream))
        log.debug("$json.destinationFolder")
        log.debug("$json.pathToFolder")
        if (json.destinationFolder != null && json.pathToFolder != null) {
            startBackup(json.destinationFolder, json.pathToFolder)
            status = 200
            message = "Sarting backing up " + json.pathToFolder + " to " + json.destinationFolder
        } else {
            status = 400
            message = "One of the parameters are missing or null"
            log.error("One of the parameters are missing or null")
        }
    }
}

jobs {

    /**
     * A job definition.
     * The first value is a unique name for the job.
     * Job runs are controlled by the provided interval or cron expression, which are mutually exclusive.
     * The actual code to run as part of the job should be part of the job's closure.
     *
     * Parameters:
     * cron (java.lang.String) - A valid cron expression used to schedule job runs (see: http://www.quartz-scheduler.org/docs/tutorial/TutorialLesson06.html)
     */
    backUpFolder(cron: "0 0/12 * 1/1 * ? *") {
        try {
            //getting the information from the properties file
            def configFile = new ConfigSlurper().parse(new File("${System.properties.'artifactory.home'}/etc/plugins/folders.properties").toURL())
            startBackup(configFile.destinationFolder as String, configFile.pathToFolder as String)

        } catch (FileNotFoundException e) {
            log.error("Properties file is missing.")
        }
    }
}

/**
 * Starts the backup operation.
 *
 * Parameters:
 * destinationFolder (String) - The destination folder
 * pathToFolder (String) -  The path to backup
 *
 */
private void startBackup(String destinationFolder, String pathToFolder) {
    log.info("Starting backing up the folders")

    // Getting repopath from the file
    RepoPath repoPath = RepoPathFactory.create(pathToFolder);
    // Getting a list of children from the provided path
    List<ItemInfo> children = repositories.getChildren(repoPath);
    log.debug("Backup path is: " + pathToFolder)
    // Checks if the path is empty or note
    if (children.size() == 0) {
        log.info("Path is empty");
        return
    }

    // Creating the main folder directory
    File mainDirectory = new File(destinationFolder + "/" + System.currentTimeMillis() + "/" + repoPath.getRepoKey())
    //checking if the destination folder exists, if not creating.
    if (!mainDirectory.exists()) {
        mainDirectory.mkdirs();
    }
    // Loop on all the direct children's of the path that would be backed.
    for (int i = 0; i < children.size(); i++) {
        // Checks if this is a folder or a file
        if (children.get(i).isFolder()) {
            // If a folder then retrieve the children
            List<ItemInfo> insideChildren = repositories.getChildren(children.get(i).getRepoPath())
            log.debug(insideChildren.size() + " Size of insideChildren")

            // Loop on all the direct children's of the children.
            for (int allChildren = 0; allChildren < insideChildren.size(); allChildren++) {
                // creating the structure
                createStructure(insideChildren, allChildren, mainDirectory)
            }
        } else {
            // Downloads a file
            downloadFileFromRoot(mainDirectory, children.get(i).getRepoPath())

        }
    }
    log.info("Finishing backing up the folder " + pathToFolder + " to " + destinationFolder)
}

/**
 * Creates the structure of the folders.
 *
 * Parameters:
 * children (List<ItemInfo>) - List of the children's
 * i (int) -  The pointer to the place in the List
 * mainDirectory (File) -  The main directory where the files and the folders would be created
 *
 */
private void createStructure(List<ItemInfo> children, int i, File mainDirectory) {
    // Retrieve the info and creates folder if the repo path is folder
    List<ItemInfo> insideChildren = getItemInfo(children.get(i).getRepoPath(), mainDirectory)
    log.debug(insideChildren.size() + " insideChildren Size within the createStructure")

    // If it's a file then create it. (We can know this if the insideChildren size is 0 this means we have reached a file
    if (insideChildren.size() == 0) {
        if (!children.get(i).getRepoPath().isFolder()) {
            log.debug(children.get(i).getRepoPath().getParent().getPath())
            // For safety creating a folder/checking if a folder exists
            createFolder(children.get(i).getRepoPath().getParent(), mainDirectory)
            downloadFileFromRoot(mainDirectory, children.get(i).getRepoPath())
        }
        // No need to continue since we reached a file.
        return
    }

    // Loop on all the direct children's of the children.
    for (int j = 0; j < insideChildren.size(); j++) {
        log.debug("Name: " + insideChildren.get(j).getName())
        log.debug("RelPath: " + insideChildren.get(j).getRelPath())

        if (insideChildren.get(j).isFolder()) {
            // We have not reached a file then let's recursive run to create a folder stracture.
            createStructure(insideChildren, j, mainDirectory)
        } else {
            log.debug("repoPath Get Path root: " + insideChildren.get(j).getRepoPath())
            downloadFileFromRoot(mainDirectory, insideChildren.get(j).getRepoPath())
        }
    }
}

/**
 * Checks if the folder exists, if not creates it.
 *
 * Parameters:
 * repoPath (RepoPath) - The RepoPath we need to create
 * mainDirectory (File) -  The main directory where the files and the folders would be created
 *
 */
private void createFolder(RepoPath repoPath, File mainDirectory) {
    log.debug("Repo Path inside Create Folder : " + repoPath.getPath())
    File directory = new File(mainDirectory.getAbsolutePath() + "/" + repoPath.getPath())
    if (!directory.exists()) {
        directory.mkdirs()
    }
}

/**
 * Returns a list of all the children's. If the folder does not exists then creates it.
 *
 * Parameters:
 * repoPath (RepoPath) - The RepoPath we need to create
 * mainDirectory (File) -  The main directory where the files and the folders would be created
 *
 */
private List<ItemInfo> getItemInfo(RepoPath repoPath, File mainDirectory) {
    log.debug("repoPath Get Path info: " + repoPath.getPath())
    if (repoPath.isFolder()) {
        File directory = new File(mainDirectory.getAbsolutePath() + "/" + repoPath.getPath())
        log.debug("Repo Path Create Folder : " + repoPath.getPath())
        if (!directory.exists()) {
            directory.mkdirs()
        }
    }

    return repositories.getChildren(repoPath);
}

/**
 * Downloads a file to the directory.
 *
 * Parameters:
 * repoPath (RepoPath) - The RepoPath we need to create
 * mainDirectory (File) -  The main directory where the files and the folders would be created
 *
 */
private void downloadFileFromRoot(File mainDirectory, RepoPath repoPath) {
    byte[] data = new byte[1024];

    FileOutputStream fos;
    File file_name = new File(mainDirectory.getAbsolutePath() + "/" + repoPath.getPath())
    log.debug("From download file " + file_name.getAbsolutePath())

    try {
        fos = new FileOutputStream(file_name);
        InputStream is;
        try {
            is = repositories.getContent(repoPath).getInputStream();
            int len;
            while ((len = is.read(data)) > 0) {
                // Checks if enough space on the hard drive.
                if (file_name.getFreeSpace() < len) {
                    throw new CancelException("There is not enough space on the Hard Drive", 400)
                }
                fos.write(data, 0, len);
            }
        } catch (IOException e) {
            log.error(e.printStackTrace())
        } finally {
            if (is != null) {
                is.close()
            }
        }
    } catch (FileNotFoundException e) {
        log.error(e.printStackTrace())
    } catch (IOException e) {
        log.error(e.printStackTrace())
    }
    finally {
        if (fos != null)
            fos.close()
    }
}
