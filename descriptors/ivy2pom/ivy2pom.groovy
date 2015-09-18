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

import groovy.util.AntBuilder
import groovy.xml.NamespaceBuilder
import org.artifactory.fs.FileLayoutInfo
import org.artifactory.repo.RepoPath
import org.artifactory.request.Request
import org.jwaresoftware.log4ant.listener.AntToSlf4jConduit

import static java.io.File.createTempFile
import static org.apache.commons.io.FileUtils.deleteQuietly
import static org.artifactory.repo.RepoPathFactory.create

final String TARGET_RELEASES_REPOSITORY = 'ext-release-local'
final String TARGET_SNAPSHOTS_REPOSITORY = 'ext-snapshot-local'
final String IVY_DESCRIPTORS_REPOSITORY = 'ivy'

download {
    afterDownloadError { Request request ->
        RepoPath repoPath = request.repoPath
        FileLayoutInfo fileLayoutInfo = repositories.getLayoutInfo(repoPath)
        if (repoPath.path.endsWith('.pom')) {
            // it was pom lookup, let's rock
            def srcPath = repoPath.path
            // [org]/[module]/[baseRev](-[folderItegRev])/[type]s/ivy-[baseRev](-[fileItegRev]).xml
            // ivy is flexible, so the repo layout may differ from the one I construct here. Change at will.
            def dstPath = fileLayoutInfo.with {
                "${organization}/$module/$baseRevision${integration ? '-' + folderIntegrationRevision : ''}/${type}s/ivy-${baseRevision}${integration ? '-' + fileIntegrationRevision : ''}.xml"
            }
            def stream = repositories.getContent(create(IVY_DESCRIPTORS_REPOSITORY, dstPath)).inputStream
            if (!stream) {
                err = "Ivy descriptor $dstPath does not exist"
                log.error(err)
                message = err
                status = 404
                return
            }
            def reader = new InputStreamReader(stream)
            log.info("Successfully retrieved ivy decriptor for $srcPath")
            // we got ivy, let's transform to pom
            File newFile = ivy2Pom(reader)
            // let's translate the path from originally requested (to repo with some layout) to the layout of the target repo
            String targetRepoKey = fileLayoutInfo.isIntegration() ? TARGET_SNAPSHOTS_REPOSITORY : TARGET_RELEASES_REPOSITORY
            String targetPath = repositories.translateFilePath(repoPath, targetRepoKey)
            RepoPath deployRepoPath = create(targetRepoKey, targetPath)
            newFile.withInputStream { repositories.deploy(deployRepoPath, it) }
            deleteQuietly(newFile)
            // and now let's return it to the user:
            def retStream = repositories.getContent(deployRepoPath).inputStream
            if (retStream != null) {
                inputStream = retStream
                status = 200
            } else {
                err = "Could not deploy ivy file to $deployRepoPath.path"
                log.error(err)
                message = err
                status = 500
            }
        }
    }
}

private File ivy2Pom(Reader reader) {
    // we'll reuse Ivy Ant task. It works with files
    File srcFile = createTempFile('ivy', '.xml')
    srcFile << reader
    File dstFile = createTempFile('pom', '.xml')
    def ant = new AntBuilder()
    // noinspection GroovyAssignabilityCheck
    ant.project.removeBuildListener ant.project.buildListeners[0]
    // remove the default logger (clear() on getBuildListeners() won't work - protective copy)
    ant.project.addBuildListener new AntToSlf4jConduit()
    def ivy = NamespaceBuilder.newInstance(ant, 'antlib:fr.jayasoft.ivy.ant')
    ivy.makepom pomFile: dstFile.absolutePath, ivyFile: srcFile.absolutePath
    deleteQuietly(srcFile)
    dstFile
}
