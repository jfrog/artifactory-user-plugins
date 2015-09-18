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

import org.artifactory.repo.RepoPath
import org.artifactory.repo.RepoPathFactory
import org.artifactory.request.Request

/**
 * Create a virtual symlink called "latest" redirecting (internally no 302) to the folderName
 * provided from the value of the 'latest.folderName' property set on the root folder
 * of the repository.
 * In this example only dist-local repo name is working.
 *
 * @author freds
 * @since 03/13/14
 */

download {
    beforeDownloadRequest { Request request, RepoPath path ->
        if (path.repoKey == "dist-local" && path.path.startsWith('latest/')) {
            // Find the folder that is latest based on property
            String folderName = repositories.getProperty(RepoPathFactory.create(path.repoKey, ''), 'latest.folderName')
            if (folderName) {
                modifiedRepoPath = RepoPathFactory.create(path.repoKey, folderName + path.path.substring('latest'.length()))
            }
        }
    }
}
