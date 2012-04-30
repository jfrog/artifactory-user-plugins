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

import org.artifactory.repo.RepoPath
import org.artifactory.request.Request
import org.jwaresoftware.log4ant.listener.AntToSlf4jConduit

import static groovyx.net.http.ContentType.TEXT
import static groovyx.net.http.Method.GET
import static java.io.File.createTempFile
import static org.apache.commons.io.FileUtils.deleteQuietly
import static org.artifactory.repo.RepoPathFactory.create

final String TARGET_RELEASES_REPOSITORY = 'ext-release-local'
final String TARGET_SNAPSHOTS_REPOSITORY = 'ext-snapshot-local'
final String IVY_DESCRIPTORS_REPOSITORY= 'ivy'

download {

    afterDownloadError { Request request ->
        RepoPath repoPath = request.repoPath
        if (repoPath.path.endsWith('.pom')) {
            def pomPath = repoPath.path
            //it was pom lookup, let's rock

            //to get layout info we have to use some repo with maven layout. The requested repo is probably not in maven layout (since it contains ivy descriptors).
            //I'll use the repo1 repo, but your repositories set may be different
            def mavenLayoutInfo = repositories.getLayoutInfo(create("repo1", pomPath))

            //ivy is flexible, so the repo layout may differ from the one I construct here. Change at will.
            def ivyFilePath = mavenLayoutInfo.with {
                "${organization}/$module/$baseRevision${integration ? '-' + folderIntegrationRevision : ''}/ivy.xml"
                        }

            //[org]/[module]/[baseRev](-[folderItegRev])/ivy.xml

            def url = "$request.servletContextUrl/$IVY_DESCRIPTORS_REPOSITORY/$ivyFilePath"
            def http = new HTTPBuilder(url)
            //Credentials options:
            //1. non-anonymous user: Since maven is not preemptive the original repo shouldn't be accessible by anonymous user.
            //   That will ensure credentials handshake on the request and we will have our user by now.
            //2. local elevation: use admin credentials hard-coded in plugin (replace the currentUsername and encrypted password here.
            //3. anonymous read and deploy: enable anonymous read of ivy descriptors and anonymous deploy of pom files.
            http.auth.basic(security.currentUsername, security.encryptedPassword == null ? '' : security.encryptedPassword)
            http.request(GET, TEXT) { req ->
                owner.log.info("Sending request to ${url}")

                response.success = { resp, Reader reader ->
                    owner.log.info("Successfully retireved ivy decriptor for ${pomPath}")
                    //we got ivy, let's transform to pom
                    File pomFile = ivy2Pom(reader)
                    //let's translate the path from originally requested (to repo with some layout) to the layout of the target repo
                    def pomDescriptorLayout = mavenLayoutInfo
                    RepoPath deployRepoPath = create(pomDescriptorLayout.isIntegration() ? TARGET_SNAPSHOTS_REPOSITORY : TARGET_RELEASES_REPOSITORY, pomPath)
                    StatusHolder statusHolder = pomFile.withInputStream {
                        repositories.deploy(deployRepoPath, it)
                    } as StatusHolder
                    deleteQuietly(pomFile)
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
                    owner.log.warn("Failed to retireve ivy descriptor for ${pomPath}: $status, $message")
                }
            }
        }
    }
}

private File ivy2Pom(Reader reader) {
    //we'll reuse Ivy Ant task. It works with files
    File ivyFile = createTempFile('ivy', '.xml')
    ivyFile << reader
    File pomFile = createTempFile('pom', '.xml')
    def ant = new AntBuilder()
    //noinspection GroovyAssignabilityCheck
    ant.project.removeBuildListener ant.project.buildListeners[0] //remove the default logger (clear() on getBuildListeners() won't work - protective copy)
    ant.project.addBuildListener new AntToSlf4jConduit()
    def ivy = NamespaceBuilder.newInstance(ant, 'antlib:fr.jayasoft.ivy.ant')
    ivy.makepom pomFile: pomFile.absolutePath, ivyFile: ivyFile.absolutePath
    deleteQuietly(ivyFile)
    pomFile
}
