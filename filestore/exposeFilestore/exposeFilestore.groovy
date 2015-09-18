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

import com.google.common.collect.Multimaps
import com.google.common.collect.SetMultimap
import org.artifactory.fs.FileInfo
import org.artifactory.repo.RepoPath

/**
 * curl -X POST -v -u admin:password "http://localhost:8080/artifactory/api/plugins/execute/exposeRepository?params=repo=repoKey|dest=destFolder"
 */

executions {
    exposeRepository() { params ->
        String repoKey = params?.get('repo')?.get(0) as String
        String destFolder = params?.get('dest')?.get(0) as String

        if (!params || !repoKey || !destFolder) {
            def errMsg = "Parameters repo and dest missing please provide them like ?params=repo=repoKey,|dest=destFolder"
            log.error(errMsg)
            status = 400
            message = errMsg
            return
        }

        def dest = new File(destFolder)
        println "Running public expose repository of $repoKey into ${dest.getAbsolutePath()}"

        def filestoreDir = new File(ctx.artifactoryHome.dataDir, 'filestore')

        long start = System.currentTimeMillis()
        long totFiles = 0
        SetMultimap<String, String> props = Multimaps.forMap([expose: 'true'])
        searches.itemsByProperties(props, repoKey).each { RepoPath repoPath ->
            def itemInfo = repositories.getItemInfo(repoPath)
            def destFile = new File(dest, repoPath.path)
            if (itemInfo.isFolder()) {
                if (destFile.exists() && !destFile.isDirectory()) {
                    destFile.delete()
                }
                if (!destFile.exists()) {
                    destFile.mkdirs()
                }
            } else {
                def sha1 = ((FileInfo) itemInfo).checksumsInfo.sha1
                def filestoreFile = new File(filestoreDir, "${sha1.substring(0, 2)}/$sha1")
                if (destFile.exists()) {
                    destFile.delete()
                }
                destFile.getParentFile().mkdirs()
                def execLn = "ln -s ${filestoreFile.getAbsolutePath()} ${destFile.getAbsolutePath()}".execute()
                if (execLn.waitFor() != 0) {
                    println "Could not link ${filestoreFile.getAbsolutePath()} to ${destFile.getAbsolutePath()}: ${execLn.errorStream}"
                } else {
                    totFiles++
                }
            }
        }
        message = "Linked $totFiles binary files, in ${System.currentTimeMillis() - start}ms\n"
        status = 200
        return
    }
}
