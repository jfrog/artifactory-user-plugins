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
import static org.artifactory.util.PathUtils.getFileName

final String TARGET_RELEASES_REPOSITORY = 'ext-release-local'
final String TARGET_SNAPSHOTS_REPOSITORY = 'ext-snapshot-local'
final String MAVEN_DESCRIPTORS_REPOSITORY = 'maven'

download {
    afterDownloadError { Request request ->
        RepoPath repoPath = request.repoPath
        FileLayoutInfo fileLayoutInfo = repositories.getLayoutInfo(repoPath)
        if (isIvyDescriptor(fileLayoutInfo, repoPath)) {
            // it was ivy lookup, let's rock
            def srcPath = repoPath.path
            // [orgPath]/[module]/[baseRev](-[folderItegRev])/[module]-[baseRev](-[fileItegRev])(-[classifier].pom
            def dstPath = fileLayoutInfo.with {
                "${organization.replace('.', '/')}/$module/$baseRevision${integration ? '-' + folderIntegrationRevision : ''}/$module-$baseRevision${integration ? '-' + fileIntegrationRevision : ''}${classifier ? '-' + classifier : ''}.pom"
            }
            def stream = repositories.getContent(create(MAVEN_DESCRIPTORS_REPOSITORY, dstPath)).inputStream
            if (!stream) {
                err = "Pom descriptor $dstPath does not exist"
                log.error(err)
                message = err
                status = 404
                return
            }
            def reader = new InputStreamReader(stream)
            log.info("Successfully retrieved ${fileLayoutInfo.module}.pom for $srcPath")
            // we got pom, let's transform to ivy
            File newFile = pom2Ivy(reader)
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
                err = "Could not deploy pom file to $deployRepoPath.path"
                log.error(err)
                message = err
                status = 500
            }
        }
    }
}

private File pom2Ivy(Reader reader) {
    // we'll reuse Ivy Ant task. It works with files
    File srcFile = createTempFile('pom', '.xml')
    srcFile << reader
    File dstFile = createTempFile('ivy', '.xml')
    def ant = new AntBuilder()
    // noinspection GroovyAssignabilityCheck
    ant.project.removeBuildListener ant.project.buildListeners[0]
    // remove the default logger (clear() on getBuildListeners() won't work - protective copy)
    ant.project.addBuildListener new AntToSlf4jConduit()
    def ivy = NamespaceBuilder.newInstance(ant, 'antlib:fr.jayasoft.ivy.ant')
    ivy.convertpom pomFile: srcFile.absolutePath, ivyFile: dstFile.absolutePath
    deleteQuietly(srcFile)
    dstFile
}

private boolean isIvyDescriptor(FileLayoutInfo fileLayoutInfo, RepoPath repoPath) {
    String fileName = getFileName(repoPath.path)
    String repoLayoutRef = repositories.getRepositoryConfiguration(repoPath.repoKey).repoLayoutRef
    fileLayoutInfo.valid && // it was search for some descriptor.
        (repoLayoutRef.contains('ivy') || // Now let's check if it was ivy. We can't be sure, Ivy is flexible. We'll do our best effort
            repoLayoutRef.contains('gradle') ||
            'ivy.xml'.equals(fileName) ||
            (fileName.startsWith('ivy-') && fileName.endsWith('.xml')) ||
            fileName.endsWith('.ivy') ||
            fileName.endsWith('-ivy.xml'))
}
