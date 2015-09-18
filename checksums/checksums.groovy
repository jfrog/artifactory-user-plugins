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

import org.apache.commons.codec.digest.DigestUtils
import org.artifactory.repo.RepoPath
import org.artifactory.request.Request

import static org.apache.commons.io.FilenameUtils.*
import static org.artifactory.repo.RepoPathFactory.create

download {
    // Algorithm names as defined in http://docs.oracle.com/javase/6/docs/technotes/guides/security/StandardNames.html
    // md5 and sha1 are omitted since they are supported by Artifactory natively
    def knownAlogrithms = ['sha256', 'sha384', 'sha512']

    afterDownloadError { Request request ->
        def requestedPath = request.repoPath.path
        if (isExtension(requestedPath, knownAlogrithms)) {
            // we only support what DigestUtils supports, look for sha*Hex() methods in http://commons.apache.org/codec/apidocs/org/apache/commons/codec/digest/DigestUtils.html
            RepoPath targetRepoPath = create(request.repoPath.repoKey, removeExtension(requestedPath))
            String extension = getExtension(requestedPath)
            def checksumPropertyName = "checksum.$extension"
            String savedChecksum = repositories.getProperty(targetRepoPath, checksumPropertyName)
            if (savedChecksum) {
                // if the checksum was already calculated and stored in properties, return it instead of the original 404
                status = 200
                size = savedChecksum.size()
                inputStream = new ByteArrayInputStream(savedChecksum.bytes)
            } else {
                // if property was not found, calculate new checksum, save as property, return it instead of 404
                def content = repositories.getContent(targetRepoPath)
                if (content.size >= 0) {
                    String checksum = content.inputStream.withStream { DigestUtils."${extension}Hex"(it) }
                    repositories.setProperty(targetRepoPath, checksumPropertyName, checksum)
                    status = 200
                    size = checksum.size()
                    inputStream = new ByteArrayInputStream(checksum.bytes)
                }
            }
        }
    }
}
