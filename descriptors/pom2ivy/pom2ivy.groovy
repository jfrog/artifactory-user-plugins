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



import groovy.xml.NamespaceBuilder
@groovy.lang.Grapes([
@GrabResolver(name = 'rjo', root = 'http://repo.jfrog.org/artifactory/libs-releases', m2compatible = 'true'),
@Grab(group = 'org.jwaresoftware.antxtras', module = 'jw-log4ant', version = '3.0.0'),
@Grab(group = 'org.codehaus.groovy.modules.http-builder', module = 'http-builder', version = '0.5.2'),
@Grab(group = 'org.apache.ant', module = 'ant', version = '1.8.3'),
@Grab(group = 'org.apache.ant', module = 'ant-launcher', version = '1.8.3'),
@GrabExclude('org.slf4j:slf4j'),
@GrabExclude('org.codehaus.groovy:groovy'),
]) import groovyx.net.http.HTTPBuilder
import org.artifactory.common.StatusHolder
import org.artifactory.fs.FileLayoutInfo
import org.artifactory.repo.RepoPath
import org.artifactory.request.Request
import org.jwaresoftware.log4ant.listener.AntToSlf4jConduit

import static groovyx.net.http.ContentType.TEXT
import static groovyx.net.http.Method.GET
import static java.io.File.createTempFile
import static org.apache.commons.io.FileUtils.deleteQuietly
import static org.artifactory.repo.RepoPathFactory.create
import static org.artifactory.util.PathUtils.getFileName

final String TARGET_RELEASES_REPOSITORY = 'ext-release-local'
final String TARGET_SNAPSHOTS_REPOSITORY = 'ext-snapshot-local'

download {

    afterDownloadError { Request request ->
        RepoPath repoPath = request.repoPath
        FileLayoutInfo fileLayoutInfo = repositories.getLayoutInfo(repoPath)
        if (isIvyDescriptor(fileLayoutInfo, repoPath)) {
            //it was ivy lookup, let's rock
            //http://repo.jfrog.org/artifactory/libs-snapshots-local/org/jfrog/bamboo/bamboo-artifactory-plugin/1.5.x-SNAPSHOT/bamboo-artifactory-plugin-1.5.x-20120419.134125-158-sources.jar
            def pomPath = fileLayoutInfo.with {
                "${organization.replace('.', '/')}/$module/$baseRevision${integration ? '-' + folderIntegrationRevision : ''}/$module-$baseRevision${integration ? '-' + fileIntegrationRevision : ''}${classifier ? '-' + classifier : ''}.pom"
            }

            def url = "$request.servletContextUrl/${repoPath.repoKey}/$pomPath"
            def http = new HTTPBuilder(url)
            http.auth.basic(security.currentUsername, security.encryptedPassword == null ? '' : security.encryptedPassword)
            http.request(GET, TEXT) { req ->
                owner.log.info("Sending request to ${url}")

                response.success = { resp, Reader reader ->
                    owner.log.info("Successfully retireved ${fileLayoutInfo.module}.pom for ${repoPath.path}")
                    //we got pom, let's transform to ivy
                    File ivyFile = pom2Ivy(reader)
                    //let's translate the path from originally requested (to repo with some layout) to the layout of the target repo
                    String targetRepoKey = fileLayoutInfo.isIntegration() ? TARGET_SNAPSHOTS_REPOSITORY : TARGET_RELEASES_REPOSITORY
                    String targetPath = repositories.translateFilePath(repoPath, targetRepoKey)
                    RepoPath deployRepoPath = create(targetRepoKey, targetPath)
                    StatusHolder statusHolder = ivyFile.withInputStream {
                        repositories.deploy(deployRepoPath, it)
                    } as StatusHolder
                    deleteQuietly(ivyFile)
                    if ((200..<300).contains(statusHolder.statusCode)) {
                        //and now let's return it to the user:
                        inputStream = repositories.getContent(deployRepoPath).inputStream
                    }
                    status = statusHolder.statusCode
                    message = statusHolder.statusMsg
                }
                response.failure = { resp ->
                    message = resp.statusLine
                    status = resp.status
                    owner.log.warn("Failed to retireve ${fileLayoutInfo.module}.pom for ${fileLayoutInfo}: $status, $message")
                }
            }
        }
    }
}

private File pom2Ivy(Reader reader) {
    //we'll reuse Ivy Ant task. It works with files
    File pomFile = createTempFile('pom', '.xml')
    pomFile << reader
    File ivyFile = createTempFile('ivy', '.xml')
    def ant = new AntBuilder()
    //noinspection GroovyAssignabilityCheck
    ant.project.removeBuildListener ant.project.buildListeners[0] //remove the default logger (clear() on getBuildListeners() won't work - protective copy)
    ant.project.addBuildListener new AntToSlf4jConduit()
    def ivy = NamespaceBuilder.newInstance(ant, 'antlib:fr.jayasoft.ivy.ant')
    ivy.convertpom pomFile: pomFile.absolutePath, ivyFile: ivyFile.absolutePath
    deleteQuietly(pomFile)
    ivyFile
}

private boolean isIvyDescriptor(FileLayoutInfo fileLayoutInfo, RepoPath repoPath) {
    String fileName = getFileName(repoPath.path)
    String repoLayoutRef = repositories.getRepositoryConfiguration(repoPath.repoKey).repoLayoutRef
    fileLayoutInfo.valid && //it was search for some descriptor.
            (repoLayoutRef.contains('ivy') || // Now let's check if it was ivy. We can't be sure, Ivy is flexible. We'll do our best effort
                    repoLayoutRef.contains('gradle') ||
                    'ivy.xml'.equals(fileName) ||
                    (fileName.startsWith('ivy-') && fileName.endsWith('.xml')) ||
                    fileName.endsWith('.ivy') ||
                    fileName.endsWith('-ivy.xml'))
}