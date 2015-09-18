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

import org.artifactory.addon.yum.InternalYumAddon
import org.artifactory.repo.LocalRepositoryConfiguration
import org.artifactory.repo.RepoPath
import org.artifactory.repo.RepoPathFactory

// usage: curl -X POST -u admin:password http://localhost:8088/artifactory/api/plugins/execute/yumCalculate?params=path=yum-repository/path/to/dir

def calculateYumMetadata(RepoPath repoPath) {
    def dirDepth = repoPath.path.empty ? 0 : repoPath.path.split('/').length
    def repoConf = (LocalRepositoryConfiguration) repositories.getRepositoryConfiguration(repoPath.repoKey)
    if (repoConf.yumRootDepth != dirDepth) {
        return "Given directory $repoPath.path not at YUM repository's configured depth"
    }
    if (repoConf.calculateYumMetadata) {
        return "YUM metadata is set to calculate automatically"
    }
    def yumBean = ctx.beanForType(InternalYumAddon.class)
    yumBean.calculateYumMetadata(repoPath)
    return 0
}

executions {
    yumCalculate(groups: ['indexers']) { params ->
        String repPath = params?.get('path')?.get(0) as String
        if (!repPath) {
            status = 400
            message = "Need a path parameter to calculate yum metadata"
            return
        }
        def repoPath = RepoPathFactory.create(repPath)
        if (!repositories.exists(repoPath)) {
            status = 404
            message = "Folder $repoPath.id to index YUM does not exists"
            return
        }
        def err
        asSystem {
            err = calculateYumMetadata(repoPath)
        }
        if (err) {
            status = 403
            message = err
        } else {
            status = 200
            message = "YUM metadata successfully calculated"
        }
    }
}
