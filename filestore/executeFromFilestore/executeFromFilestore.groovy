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

import groovy.json.JsonSlurper
import org.apache.commons.io.IOUtils
import org.artifactory.fs.ItemInfo
import org.artifactory.repo.RepoPath
import org.artifactory.repo.RepoPathFactory
import org.artifactory.repo.Repositories
import org.artifactory.resource.ResourceStreamHandle
import org.slf4j.Logger

import java.nio.file.Files

/**
 * curl -T execCommand.json -X POST -v -u admin:password "http://localhost:8080/artifactory/api/plugins/execute/copyAndExecute"
 * With execCommand.json looks like:
 * {
 *   "srcRepo" : "repoKey",
 *   "srcDir" : "dirPath",
 *   "destLocalDir" : "Local dir name where to copy the files",
 *   "params" : "parameters to add to the command after copy"
 * }
 */

class ExecuteConstants {
    static String copyCommand = 'cp -pu'
    static String baseExecute = 'find {destLocalDir} {params}'
    static String[] excludeExtensions = ['.txt', '.log']
}

executions {
    copyAndExecute(version: '1.0',
        description: 'Copy repoPath to local drive and execute command',
        groups: ['executors'].toSet()) { ResourceStreamHandle body ->
        assert body
        def json = new JsonSlurper().parse(new InputStreamReader(body.inputStream))

        String repoKey = json.srcRepo as String
        String srcDir = json.srcDir as String
        String destLocalDir = json.destLocalDir as String
        String params = json.params as String

        long start = System.currentTimeMillis()
        LocalLog localLog = new LocalLog(start, log)
        if (!repoKey || !destLocalDir) {
            localLog.error(
                "One parameter repoKey, destLocalDir is missing please provide them in your input JSON body")
            status = 400
            message = localLog.finalMessage()
            return
        }
        if (!srcDir) srcDir = ""

        RepoPath srcRepoPath = RepoPathFactory.create(repoKey, srcDir)
        // Verify repo path exists and is a folder
        def srcItemInfo = repositories.getItemInfo(srcRepoPath)
        if (srcItemInfo == null || !srcItemInfo.isFolder()) {
            localLog.error("Source path ${srcRepoPath.id} does not exists or is noty a folder")
            status = 400
            message = localLog.finalMessage()
            return
        }

        def dest = new File(destLocalDir)
        status = createDir(localLog, dest)
        if (status != 200) {
            message = localLog.finalMessage()
            return
        }

        // This line uses private API and may break in the future
        File filestoreDir = new File(ctx.artifactoryHome.dataDir, 'filestore')

        localLog.info("Running copy of ${srcRepoPath.id} into ${dest.getAbsolutePath()} log at ${localLog.logFile.getAbsolutePath()}")
        long totFiles = copyRecursive(localLog, repositories, srcItemInfo, dest, filestoreDir)
        localLog.info("Copied $totFiles binary files, in ${System.currentTimeMillis() - start}ms")

        def execCommand = ExecuteConstants.baseExecute.replace('{destLocalDir}', dest.getAbsolutePath())
        if (params) {
            execCommand = execCommand.replace('{params}', params)
        }
        localLog.info("Executing: $execCommand")
        def execLn = execCommand.execute()
        def res = execLn.waitFor()
        localLog.info("Output:\n${execLn.text}")
        if (res != 0) {
            localLog.error("Could not execute ${execCommand}: ${IOUtils.toString(execLn.errorStream)}")
            status = 500
        } else {
            localLog.info("Total successful script execution in ${System.currentTimeMillis() - start}ms")
            status = 200
        }
        message = localLog.finalMessage()
        return
    }
}

def createDir(LocalLog localLog, File dir) {
    if (dir.exists() && !dir.isDirectory()) {
        localLog.info("Deleting file that should be a dir: ${dir.getAbsolutePath()}")
        if (!dir.delete()) {
            localLog.error("Could not delete ${dir.getAbsolutePath()}")
            return 507
        }
    }
    if (!dir.exists()) {
        localLog.debug("Creating dir: ${dir.getAbsolutePath()}")
        if (!dir.mkdirs()) {
            localLog.error("Could not create ${dir.getAbsolutePath()}")
            return 507
        }
    }
    if (!Files.isWritable(dir.toPath())) {
        localLog.error("Cannot write to ${dir.getAbsolutePath()}")
        return 507
    }
    return 200
}

class LocalLog {
    long id
    Logger log
    File logFile
    List<String> messages = []

    LocalLog(id, log) {
        this.id = id
        this.log = log
        this.logFile = new File("/tmp/copyAndExec-${id}.log")
    }

    String finalMessage() {
        messages.join('\n') + '\n'
    }

    def error(String msg) {
        log.error(msg)
        logFile << msg
        // The response messages are only aggregating info and above
        messages << msg
    }

    def info(String msg) {
        log.info(msg)
        logFile << msg
        // The response messages are only aggregating info and above
        messages << msg
    }

    def debug(String msg) {
        log.debug(msg)
        // debug only in log file
        logFile << msg
    }
}

long copyRecursive(LocalLog localLog, Repositories repositories, ItemInfo item, File destFolder, File filestoreDir) {
    def name = item.name
    if (item.isFolder()) {
        File newDestFolder = new File(destFolder, name)
        long tot = 0L
        if (createDir(localLog, newDestFolder) == 200) {
            repositories.getChildren(item.repoPath).each {
                tot += copyRecursive(localLog, repositories, it, newDestFolder, filestoreDir)
            }
        }
        return tot
    } else {
        for (String excl : ExecuteConstants.excludeExtensions) {
            if (name.endsWith(excl)) return 0L
        }
        def sha1 = item.sha1
        def cpCommand = """${ExecuteConstants.copyCommand} ${filestoreDir}/${sha1.substring(0, 2)}/$sha1 ${
            destFolder.getAbsolutePath()
        }/${name}"""
        localLog.debug("Executing: $cpCommand")
        def execLn = cpCommand.execute()
        if (execLn.waitFor() != 0) {
            localLog.error("Could not execute ${cpCommand}: ${IOUtils.toString(execLn.errorStream)}")
            return 0L
        } else {
            return 1L
        }
    }
}
