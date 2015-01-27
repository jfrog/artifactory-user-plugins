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

/*
 * Copyright 2014 JFrog Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * An example on how to use internal rewrite plugin.
 * Here requests to dist-local/latest/XXX will be redirected (without sending a 302 to the client) to
 * dist-local/[folder]/XXX where folder is the value of the 'latest.folderName' property on the root
 * folder dist-local.
 *
 * Date: 3/13/14 3:43 PM
 * @author freds
 */

download {
    beforeDownloadRequest { Request request, RepoPath path ->
        if (path.repoKey == "md5-test-remote" && path.path.endsWith('.md5')) {
            // modify the extension of the file and add '.txt' to it
            modifiedRepoPath = RepoPathFactory.create(path.repoKey, path.path + '.txt')
        }
    }
}
