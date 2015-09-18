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
 * An example on how to use internal rewrite plugin.
 * Instead of saving nupkg files as flat in a remote repository, we save them in
 * a 2 level folder hierarchy
 *
 * nuget-gallery/NHibernate.3.3.1.4000.nupkg -> nuget-gallery/NHibernate/NHibernate/NHibernate.3.3.1.4000.nupkg
 *
 * @author Shay Yaakov
 */

download {
    beforeDownloadRequest { Request request, RepoPath repoPath ->
        if (repoPath.repoKey == "nuget-gallery" && repoPath.path.endsWith('.nupkg')) {
            // api/v2/package/jQuery/2.0.1
            String[] pathElements = request.alternativeRemoteDownloadUrl.split("/")
            String pkgName = pathElements[pathElements.length - 2]
            String pkgVersion = pathElements[pathElements.length - 1]

            String finalPath = pkgName + '/' + pkgName + '/' + pkgName + '.' + pkgVersion + '.nupkg'
            log.info "Transforming NuGet download from ${repoPath.path} to ${finalPath}"
            modifiedRepoPath = RepoPathFactory.create(repoPath.repoKey, finalPath)
        }
    }
}
