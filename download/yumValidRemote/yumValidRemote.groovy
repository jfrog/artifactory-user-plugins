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

import org.artifactory.api.common.MoveMultiStatusHolder
import org.artifactory.repo.RepoPathFactory
import org.artifactory.repo.service.InternalRepositoryService
import org.artifactory.repo.service.mover.DefaultRepoPathMover
import org.artifactory.repo.service.mover.MoverConfigBuilder
import org.artifactory.request.NullRequestContext
import org.artifactory.schedule.CachedThreadPoolTaskExecutor

downloadMutex = new Object()
downloadQueue = [:]

// The Artifactory thread pool, necessary to download metadata files as a
// background task
threadPool = ctx.beanForType(CachedThreadPoolTaskExecutor)

download {
    // If we're getting from remote a repomd.xml file with legacy repodata file
    // names, change those names to the modern style, and cache these files.
    altRemoteContent { repoPath ->
        // don't modify the content unless we're dealing with a repomd.xml
        if (!(repoPath.path ==~ '^(?:.+/)?repodata/repomd\\.xml$')) return
        def repoService = ctx.beanForType(InternalRepositoryService)
        def repo = repoService.remoteRepositoryByKey(repoPath.repoKey)
        def streamhandle = null, xml = null
        try {
            // open a stream to the remote and parse the repomd.xml file
            log.info("Downloading and parsing repomd.xml")
            streamhandle = repo.downloadResource(repoPath.path)
            xml = new XmlParser().parseText(streamhandle.inputStream.text)
        } finally {
            // close the input stream
            streamhandle?.close()
        }
        if (!xml) return
        // add the checksums to the file names, if they aren't already there
        def downloads = []
        xml.each {
            // do nothing if this node is the wrong type
            if (it.name() != 'data' && it.name()?.localPart != 'data') return
            // do nothing if there is no file path
            def path = it?.location?.@href
            if (!path) return
            // do nothing if the checksum does not exist or is the wrong type
            if (!(it?.checksum?.@type[0] ==~ '^sha1?$')) return
            def checksum = it.checksum.text()
            // do nothing if the file path is not a legacy repodata path
            def match = path[0] =~ '^((?:.+/)?repodata/)([^/]+)$'
            if (!match || match[0][2].startsWith("$checksum-")) return
            // if everything checks out, modify the file path
            log.info("Found legacy repodata file '{}'", path[0])
            def newname = "${match[0][1]}$checksum-${match[0][2]}"
            it.location.@href = newname
            // if the file already exists in the cache, we're done
            def npath = RepoPathFactory.create(repoPath.repoKey, newname)
            if (repositories.exists(npath)) return
            // if the file doesn't exist, add it to the list to download
            synchronized (downloadMutex) {
                if (!downloadQueue[repo.key + ':' + path[0]]) {
                    downloadQueue[repo.key + ':' + path[0]] = true
                    downloads << [path[0], newname, match]
                }
            }
        }
        // if all metadata files already exist in the cache, return the new
        // repomd.xml
        if (!downloads) {
            log.info("Serving most recent metadata")
            // write the modified xml to a string
            def writer = new StringWriter()
            def printer = new XmlNodePrinter(new PrintWriter(writer))
            printer.preserveWhitespace = true
            printer.print(xml)
            def newXml = writer.toString()
            // set the new string as the new file content
            size = newXml.length()
            inputStream = new ByteArrayInputStream(newXml.bytes)
            return
        }
        // in separate threads, cache any necessary metadata files
        downloads.each { download ->
            threadPool.submit {
                def (path, newname, match) = download
                // download the new file
                log.info("Repodata file '{}' not cached, downloading", newname)
                def opath = RepoPathFactory.create(repoPath.repoKey, path)
                def ctx = new NullRequestContext(opath)
                def res = repo.getInfo(ctx)
                repo.getResourceStreamHandle(ctx, res)?.close()
                // move the file to the appropriate location
                def cachekey = repo.localCacheRepo.key
                def truesha = repositories.getFileInfo(opath).checksumsInfo.sha1
                def truename = "${match[0][1]}$truesha-${match[0][2]}"
                def truepath = RepoPathFactory.create(cachekey, truename)
                def oldpath = RepoPathFactory.create(cachekey, path)
                def status = new MoveMultiStatusHolder()
                def config = new MoverConfigBuilder(oldpath, truepath)
                config.failFast(true).atomic(true)
                def mover = new DefaultRepoPathMover(status, config.build())
                def mvsrc = repo.localCacheRepo.getImmutableFsItem(oldpath)
                def mvdst = repoService.getRepoRepoPath(truepath)
                mover.moveOrCopyMultiTx(mvsrc, mvdst)
                log.info("Completed download of '{}'", truename)
                synchronized (downloadMutex) {
                    downloadQueue.remove(repo.key + ':' + path)
                }
            }
        }
        // if there is no cached repomd.xml, return the new one
        if (!repositories.exists(repoPath)) {
            log.info("Serving most recent metadata")
            log.warn("Some metadata files may 404 until they're fully cached")
            // write the modified xml to a string
            def writer = new StringWriter()
            def printer = new XmlNodePrinter(new PrintWriter(writer))
            printer.preserveWhitespace = true
            printer.print(xml)
            def newXml = writer.toString()
            // set the new string as the new file content
            size = newXml.length()
            inputStream = new ByteArrayInputStream(newXml.bytes)
            return
        }
        // just return the old repomd.xml
        log.info("Serving old cached metadata")
        def stream = null, oldxml = null
        try {
            // get the contents of the old repomd.xml
            stream = repositories.getContent(repoPath)
            oldxml = stream.inputStream.bytes
        } finally {
            // close the input stream
            stream?.close()
        }
        size = oldxml.length
        inputStream = new ByteArrayInputStream(oldxml)
    }
}
