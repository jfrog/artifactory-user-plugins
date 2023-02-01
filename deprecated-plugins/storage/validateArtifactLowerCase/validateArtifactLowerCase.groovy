/*
 * Copyright (C) 2018 JFrog Ltd.
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
import groovy.json.JsonSlurper

def repositories = new JsonSlurper().parse(new File(ctx.artifactoryHome.etcDir, 'plugins/validateArtifactLowerCase.json'))

storage {
    beforeCreate { item ->
        if (item.repoPath.repoKey in repositories) {
            if (item.name == item.name.toLowerCase()) {
                log.info("all are lowercase and import is success")
            } else {
                throw new CancelException("Please upload Artifacts with lowercase", 403)
            }
        }
    }
}
