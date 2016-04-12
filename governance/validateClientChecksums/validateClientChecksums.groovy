/*
 * Copyright (C) 2016 JFrog Ltd.
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

/**
 * @author Uriah Levy
 */

import org.artifactory.fs.FileInfo

download {
    altResponse { request, responseRepoPath ->
        FileInfo info = repositories.getFileInfo(responseRepoPath)
        checksumsInfo = info.getChecksumsInfo()
        checksumInfo = checksumsInfo.getChecksums()

        // .getOriginal() returns the original checksum value published by the client
        // .getActual() returns the checksum value Artifactory calculated for the artifact 
        checksumInfo.each { // check both Md5 and Sha1
            log.warn("Original: " + it.getOriginal() + " Actual: " + it.getActual())
            if(it.getOriginal() == null) { // The client didn't provide a checksum or provided one the was rejected; reject
                status = 409
                message = "Download rejected: this artifact has no original checksum"
                log.warn "Download rejected: this artifact has no original checksum"
            }
        }
    }
}
