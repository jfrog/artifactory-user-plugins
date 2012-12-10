import org.artifactory.repo.RepoPath
import org.artifactory.fs.FileInfo
import com.google.common.collect.SetMultimap
import com.google.common.collect.Multimaps;

/*
* Artifactory is a binaries repository manager.
* Copyright (C) 2012 JFrog Ltd.
*
* Artifactory is free software: you can redistribute it and/or modify
* it under the terms of the GNU Lesser General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* Artifactory is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public License
* along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
*/

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
        SetMultimap<String, String> props = Multimaps.forMap([expose:'true'])
        searches.itemsByProperties(props , repoKey).each { RepoPath repoPath ->
            def itemInfo = repositories.getItemInfo(repoPath)
            def destFile = new File(dest, repoPath.path)
            if (itemInfo.isFolder()) {
                if (destFile.exists() && !destFile.isDirectory()) {
                    destFile.delete();
                }
                if (!destFile.exists()) {
                    destFile.mkdirs()
                }
            } else {
                def sha1 = ((FileInfo) itemInfo).checksumsInfo.sha1
                def filestoreFile = new File(filestoreDir, "${sha1.substring(0,2)}/$sha1")
                if (destFile.exists()) {
                    destFile.delete();
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
        message = "Linked $totFiles binary files, in ${System.currentTimeMillis()-start}ms\n"
        status = 200
        return
    }
}
