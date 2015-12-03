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

import org.artifactory.exception.CancelException

storage {
    beforeCreate { item ->
        if (!item.isFolder()){
            // Get the layout information of the item
            def layoutInfo = repositories.getLayoutInfo(item.repoPath)
            String groupId = layoutInfo.getOrganization()
            String artifactId = layoutInfo.getModule()
            String versionId = layoutInfo.getBaseRevision()
            // If the item doesn't contain the Maven Layout structure, reject
            // the upload
            if ("${groupId}" == "null" || "${artifactId}" == "null" ||
                "${versionId}" == "null") {
                status = 403
                message = 'This artifact did not match the layout.'
                log.warn message
                throw new CancelException(message, status) {
                    public Throwable fillInStackTrace() {
                        return null;
                    }
                }
            }
        }
    }
}
