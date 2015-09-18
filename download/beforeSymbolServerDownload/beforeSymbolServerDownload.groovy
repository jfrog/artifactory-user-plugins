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
import org.artifactory.request.Request

/**
 * This plugin will send the right header to the remote symbol server
 *
 * @author Michal Reuven
 */

download {
    beforeRemoteDownload { Request request, RepoPath repoPath ->
        try {
            log.debug "symbol server download plugin was called for ${repoPath.repoKey}"
            if (repoPath.repoKey == "microsoft-symbols") {
                def map = ["User-Agent": "Microsoft-Symbol-Server/6.3.9600.17095"]
                headers = map
                log.debug 'plugin beforeRemoteDownload called. header was added'
            }
        } catch (Error e) {
            log.error(e.getMessage(), e)
        }
    }
}
