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

import org.artifactory.addon.pypi.*
import org.artifactory.model.common.RepoPathImpl

/**
 *
 * @author aaronr
 * @since 08/07/15
 */

// this is REST-executed plugin
executions {
    // this execution is named 'getPypiMetadata' and it will be called by REST by this name
    // map parameters provide extra information about this execution, such as version, description, users that allowed to call this plugin, etc.
    // The expected (and mandatory) parameter is a Pypi repository/file path from which metadata will be extracted.
    // curl -X POST -v -u admin:password "http://localhost:8081/artifactory/api/plugins/execute/getPypiMetadata?params=repoPath=/3.3/s/six/six-1.9.0-py2.py3-none-any.whl|repoKey=pypi-remote-cache"
    getPypiMetadata() { params ->
        if (!params || !params.repoPath.get(0) || !params.repoKey.get(0)) {
            def errorMessage = 'path parameter is mandatory, please supply it.'
            log.error errorMessage
            status = 400
            message = errorMessage
        } else {
            def pypiPath = new RepoPathImpl(params.repoKey.get(0), params.repoPath.get(0))
            def pipService = ctx.beanForType(InternalPypiService.class)
            def pypiMetadata = pipService.getPypiMetadata(pypiPath)
            message = pypiMetadata.toString()
        }
    }
}
