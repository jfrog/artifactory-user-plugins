/*
 * Copyright (C) 2017 JFrog Ltd.
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

import groovy.transform.Field
import groovy.json.JsonSlurper
import org.artifactory.exception.CancelException

@Field final String FILE_PATH = 'plugins/restrictOverwrite.json'

storage {
    beforeCreate { item ->
        restrictOverwrite(item.repoPath)
    }
    beforeMove { item, targetRepoPath, properties ->
        restrictOverwrite(targetRepoPath)
    }
    beforeCopy { item, targetRepoPath, properties ->
        restrictOverwrite(targetRepoPath)
    }
}

def restrictOverwrite(rpath) {
    def artetc = ctx.artifactoryHome.etcDir
    def config = new File(artetc, FILE_PATH)
    def repos = new JsonSlurper().parse(config)
    // if the repository is not in our list, we don't deal with it
    if (!(rpath.repoKey in repos)) return
    // if we are not overwriting anything, it's fine
    if (!repositories.exists(rpath)) return
    // if we are overwriting a folder with another folder, it's fine
    if (rpath.isFolder() && repositories.getItemInfo(rpath).isFolder()) return
    // otherwise, disallow this write
    throw new CancelException("Overwrite of file $rpath is not allowed.", 409)
}
