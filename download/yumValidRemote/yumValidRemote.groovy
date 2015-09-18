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

import org.artifactory.repo.RepoPathFactory

import java.security.MessageDigest

download {
    // If we're getting from remote a repomd.xml file with legacy repodata file
    // names, change those names to the modern style.
    altRemoteContent { repoPath ->
        // don't modify the content unless we're dealing with a repomd.xml
        def rpath = repoPath.path
        if (!(rpath ==~ '^(?:.+/)?repodata/repomd\\.xml$')) return
        def conn = null, istream = null
        try {
            def conf = repositories.getRepositoryConfiguration(repoPath.repoKey)
            // get the url of the remote repo
            def url = conf.url
            if (!url.endsWith('/')) url += '/'
            url += rpath
            // get the remote authorization data
            def auth = "$conf.username:$conf.password".bytes.encodeBase64()
            // open a connection to the remote
            conn = new URL(url).openConnection()
            conn.setRequestProperty('Authorization', "Basic $auth")
            // make sure the remote sends the correct response
            def response = conn.responseCode
            if (response < 200 || response >= 300) return
            // parse the repomd.xml file
            istream = conn.inputStream
            def xml = new XmlParser().parse(istream)
            // add the checksums to the file names, if they aren't already there
            xml.each {
                // do nothing if this node is the wrong type
                if (it.name() != 'data' && it.name()?.getLocalPart() != 'data')
                    return
                // do nothing if there is no file path
                def path = it?.location?.@href
                if (path == null || path.isEmpty()) return
                // do nothing if the file path is not a legacy repodata path
                def match = path[0] =~ '^((?:.+/)?repodata/)([^/]+)$'
                if (!match) return
                // do nothing if the checksum does not exist or is the wrong
                // type
                if (!(it?.checksum?.@type[0] ==~ '^sha1?$')) return
                // if everything checks out, modify the file path
                def checksum = it.checksum.text()
                it.location.@href = "${match[0][1]}$checksum-${match[0][2]}"
            }
            // write the modified xml to a string
            def writer = new StringWriter()
            def printer = new XmlNodePrinter(new PrintWriter(writer))
            printer.preserveWhitespace = true
            printer.print(xml)
            def newXml = writer.toString()
            // set the new string as the new file content
            size = newXml.length()
            inputStream = new ByteArrayInputStream(newXml.bytes)
        } finally {
            // close everything
            istream?.close()
            conn?.disconnect()
        }
    }

    // If we're getting from remote a modern repodata file that doesn't exist,
    // yet a legacy file with the same checksum does, return that file instead.
    beforeDownloadRequest { request, repoPath ->
        def rpath = repoPath.path, rkey = repoPath.repoKey
        // don't modify the path unless we're in a remote repo
        if (!repositories.remoteRepositories.contains(rkey)) return
        // don't modify the path if the file is already in the cache
        if (repositories.exists(RepoPathFactory.create("$rkey-cache", rpath)))
            return
        // don't modify the path unless we're dealing with a repodata file
        def match = rpath =~ '^(?:.+/)?repodata/([0-9a-f]+)-[^/]+$'
        if (!match) return
        // get the url of the remote repo
        def conf = repositories.getRepositoryConfiguration(rkey)
        def url = conf.url
        if (!url.endsWith('/')) url += '/'
        url += rpath
        // get the remote authorization data
        def auth = "$conf.username:$conf.password".bytes.encodeBase64()
        def conn = null, istream = null, realchecksum = null
        try {
            // open a connection to the remote
            conn = new URL(url).openConnection()
            conn.setRequestMethod('HEAD')
            conn.setRequestProperty('Authorization', "Basic $auth")
            // don't modify the path if this file already exists on the far end
            if (conn.responseCode != 404) return
        } finally {
            // close everything
            istream?.close()
            conn?.disconnect()
        }
        // extract the checksum, and make a legacy version of the filename
        def checksum = match[0][1]
        def newPath = rpath - ~"$checksum-(?![^/]*/)"
        // check if this file is in the cache already
        def cachedPath = RepoPathFactory.create("$rkey-cache", newPath)
        def exists = repositories.exists(cachedPath)
        if (exists) {
            def fileInfo = repositories.getFileInfo(cachedPath)
            realchecksum = fileInfo.checksumsInfo.sha1
            // if the legacy file is cached, but it has the wrong checksum,
            // expire it from the cache
            if (checksum != realchecksum) {
                expired = true
                exists = false
            }
        }
        // if the legacy file is not cached or was expired by the previous
        // operation, attempt to pull the checksum from the far end
        if (!exists) {
            // get the url of the remote repo
            url = conf.url
            if (!url.endsWith('/')) url += '/'
            url += newPath
            // get the remote authorization data
            conn = null
            istream = null
            try {
                // open a connection to the remote
                conn = new URL(url).openConnection()
                conn.setRequestProperty('Authorization', "Basic $auth")
                // don't modify the path if the source file does not exist
                def response = conn.responseCode
                if (response < 200 || response >= 300) return
                // calculate the checksum of the response data
                istream = conn.inputStream
                def digest = MessageDigest.getInstance('SHA1')
                def buf = new byte[4096]
                def len = istream.read(buf)
                while (len != -1) {
                    digest.update(buf, 0, len)
                    len = istream.read(buf)
                }
                realchecksum = digest.digest().encodeHex().toString()
            } finally {
                // close everything
                istream?.close()
                conn?.disconnect()
            }
        }
        // don't modify the path if the requested checksum differs from that of
        // the source file
        if (checksum != realchecksum) return
        // if everything checks out, modify the path
        modifiedRepoPath = RepoPathFactory.create(rkey, newPath)
    }
}
