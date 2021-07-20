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

import java.util.concurrent.Executors

import org.artifactory.api.common.MoveMultiStatusHolder
import org.artifactory.concurrent.ArtifactoryRunnable
import org.artifactory.repo.RepoPathFactory
import org.artifactory.repo.service.InternalRepositoryService
import org.artifactory.repo.service.mover.DefaultRepoPathMover
import org.artifactory.repo.service.mover.MoverConfigBuilder
import org.artifactory.request.NullRequestContext
import org.artifactory.schedule.CachedThreadPoolTaskExecutor
import org.artifactory.storage.fs.service.FileService

import org.springframework.security.core.context.SecurityContextHolder

// The number of milliseconds to sleep before reverting a repomd.xml's iteminfo
// (see the storage hook for more details). This exists to avoid a race
// condition which results in the cached file having the same lastModified
// timestamp as a more recent version of the file from the remote server,
// causing it to refuse to update until after the remote server updates the file
// again. If you encounter this problem, try increasing this value.
etagSleep = 100

// This map contains the file paths of all metadata files that are currently in
// the process of downloading. This keeps the same file from being downloaded by
// multiple threads at the same time.
downloadMutex = new Object()
downloadQueue = [:]

// This map contains the original file paths, etags, and iteminfos for all
// repomd.xml files, so that these files can be reverted to a previous state
// when necessary (see the storage hook for more details). The etagWorking flag
// keeps the storage hook from running recursively.
etagMutex = new Object()
etagQueue = [:]
etagWorking = false

// The thread pool, necessary to download metadata files as a background task
threadPool = Executors.newFixedThreadPool(10)

storage {
    // If a new repomd.xml is available, but none of the other necessary
    // metadata files are cached yet, we serve the old repomd.xml instead by
    // sending it as the alternate content. However, the new file's etag and
    // lastModified timestamp are still added, so Artifactory will think it's
    // the new file. This hook changes the etag and timestamps back after
    // Artifactory updates the file, so that it still matches the old file.
    afterPropertyCreate { item, name, values ->
        // don't do anything here unless this is an etag property on a
        // repomd.xml file in a remote cache
        if (!item.repoKey.endsWith('-cache')) return
        if (name != "artifactory.internal.etag") return
        if (!(item.relPath ==~ '^(?:.+/)?repodata/repomd\\.xml$')) return
        synchronized (etagMutex) {
            // if we're recursing, stop
            if (etagWorking) return
            // if this file is new, save its info
            if (!(item.repoPath.id in etagQueue)) {
                etagQueue[item.repoPath.id] = [values[0], item]
                log.info("Saved etag ${values[0]} for path $item.repoPath.id")
                return
            }
        }
        // start a thread, so we can wait for the original thread to update the
        // file, before reverting it to its proper state
        def task = [run:{
            synchronized (etagMutex) {
                asSystem {
                    try {
                        // we are in a potentially recursive state: reverting
                        // the etag property on this file invokes the
                        // afterPropertyCreate hook, which calls this function
                        etagWorking = true
                        // sleep until after the new data is written
                        Thread.sleep(etagSleep)
                        // load the replacement data and write it to the file
                        def (etag, info) = etagQueue[item.repoPath.id]
                        repositories.setProperty(item.repoPath, name, etag)
                        def fileserv = ctx.beanForType(FileService)
                        def fileid = null
                        try {
                            fileid = fileserv.getNodeId(item.repoPath)
                        } catch (groovy.lang.MissingMethodException ex) {
                            fileid = fileserv.getFileNodeId(item.repoPath)
                        }
                        fileserv.updateFile(fileid, info)
                        log.info("Wrote etag $etag to path $item.repoPath.id")
                    } catch (Exception ex) {
                        def writer = new StringWriter()
                        writer.withPrintWriter { ex.printStackTrace(it) }
                        log.error(writer.toString())
                    } finally {
                        // we are no longer in a potentially recursive state
                        etagWorking = false
                    }
                }
            }
        }] as Runnable
        def authctx = SecurityContextHolder.context.authentication
        threadPool.submit(new ArtifactoryRunnable(task, ctx, authctx))
    }
}

download {
    // If we're getting from remote a repomd.xml file with legacy repodata file
    // names, change those names to the modern style, and cache these files.
    altRemoteContent { repoPath ->
        // don't modify the content unless we're dealing with a repomd.xml
        if (!(repoPath.path ==~ '^(?:.+/)?repodata/repomd\\.xml$')) return
        def pathmatch = repoPath.path =~ '^(.+/)?repodata/repomd\\.xml$'
        def prefix = pathmatch[0][1] ?: ''
        def repoService = ctx.beanForType(InternalRepositoryService)
        def repo = repoService.remoteRepositoryByKey(repoPath.repoKey)
        def cache = repo.localCacheRepo
        def xmlpath = repoPath.path
        def streamhandle = null, xml = null
        try {
            // open a stream to the remote and parse the repomd.xml file
            log.info("Downloading and parsing repomd.xml")
            streamhandle = repo.downloadResource(xmlpath)
            xml = new XmlParser().parseText(streamhandle.inputStream.text)
        } finally {
            // close the input stream
            streamhandle?.close()
        }
        if (!xml) return
        // add the checksums to the file names, if they aren't already there
        def modified = false, downloads = []
        xml.each {
            // do nothing if this node is the wrong type
            if (it.name() != 'data' && it.name()?.localPart != 'data') return
            // do nothing if there is no file path
            def origname = it?.location?.@href?.getAt(0)
            if (!origname) return
            // do nothing if the checksum does not exist or is the wrong type
            if (!(it?.checksum?.@type?.getAt(0) ==~ '^sha1?$')) return
            def checksum = it.checksum.text()
            // do nothing if the file path is not a legacy repodata path
            def match = origname =~ '^((?:.+/)?repodata/)([^/]+)$'
            if (!match || match[0][2].startsWith("$checksum-")) return
            // if everything checks out, modify the file path
            def origloc = prefix + origname
            log.info("Found legacy repodata file '$origloc'")
            def newname = "${match[0][1]}$checksum-${match[0][2]}"
            it.location.@href = newname
            modified = true
            // if the file already exists in the cache, we're done
            def newloc = prefix + newname
            def newpath = RepoPathFactory.create(repo.key, newloc)
            if (repositories.exists(newpath)) return
            // if the file doesn't exist, add it to the list to download
            synchronized (downloadMutex) {
                if (!downloadQueue[repo.key + ':' + origloc]) {
                    downloadQueue[repo.key + ':' + origloc] = true
                    downloads << [match[0][1], match[0][2], origloc]
                }
            }
        }
        // if the file went unchanged, just exit
        if (!modified) return
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
            // remove the old etag data so we can replace it later
            synchronized (etagMutex) {
                def etagPath = RepoPathFactory.create(cache.key, xmlpath)
                etagQueue.remove(etagPath.id)
            }
            // set the new string as the new file content
            size = newXml.length()
            inputStream = new ByteArrayInputStream(newXml.bytes)
            return
        }
        // in separate threads, cache any necessary metadata files
        downloads.each { download ->
            def task = [run:{
                asSystem {
                    def (matchl, matchr, origloc) = download
                    def origpath = RepoPathFactory.create(repo.key, origloc)
                    def origcache = RepoPathFactory.create(cache.key, origloc)
                    try {
                        // do a HEAD request and check for a matching etag
                        def resource = repo.retrieveInfo(origloc, false, null)
                        if (!resource.isFound()) {
                            def msg = "Repodata file '$origloc' not found"
                            msg += " on remote server"
                            log.info(msg)
                            return
                        }
                        def etagprop = "artifactory.internal.etag"
                        def parent = origpath.parent
                        def item = repositories.getChildren(parent).find {
                            def path = it.repoPath
                            def etag = repositories.getProperty(path, etagprop)
                            return etag && etag == resource.etag
                        }
                        if (item) {
                            def msg = "Repodata file '$origloc' already exists"
                            msg += " in cache as '$item.relPath"
                            log.info(msg)
                            return
                        }
                        // download the new file
                        def msg = "Repodata file '$origloc' not cached,"
                        msg += " downloading ..."
                        log.info(msg)
                        def ctx = new NullRequestContext(origpath)
                        def res = repo.getInfo(ctx)
                        repo.getResourceStreamHandle(ctx, res)?.close()
                        // move the file to the appropriate location
                        def trueinfo = repositories.getFileInfo(origpath)
                        def truesha = trueinfo.checksumsInfo.sha1
                        def trueloc = prefix + matchl + truesha + '-' + matchr
                        def truecache = RepoPathFactory.create(cache.key, trueloc)
                        def status = new MoveMultiStatusHolder()
                        def build = new MoverConfigBuilder(origcache, truecache)
                        def config = build.failFast(true).atomic(true).build()
                        def mover = new DefaultRepoPathMover(status, config)
                        def mvsrc = cache.getImmutableFsItem(origcache)
                        def mvdst = repoService.getRepoRepoPath(truecache)
                        mover.moveFileMultiTx(mvsrc, mvdst)
                        log.info("Completed download of '$trueloc'")
                    } catch (Exception ex) {
                        def writer = new StringWriter()
                        writer.withPrintWriter { ex.printStackTrace(it) }
                        log.error(writer.toString())
                    } finally {
                        synchronized (downloadMutex) {
                            downloadQueue.remove(repo.key + ':' + origloc)
                        }
                    }
                }
            }] as Runnable
            def authctx = SecurityContextHolder.context.authentication
            threadPool.submit(new ArtifactoryRunnable(task, ctx, authctx))
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
            // remove the old etag data so we can replace it later
            synchronized (etagMutex) {
                def etagPath = RepoPathFactory.create(cache.key, xmlpath)
                etagQueue.remove(etagPath.id)
            }
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
