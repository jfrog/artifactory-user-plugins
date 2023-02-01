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


import org.artifactory.exception.CancelException
import org.artifactory.repo.RepoPath

executions {
    /**
     * Returns GAVC by SHA1.
     * Usage: curl -u admin:password http://localhost:8081/artifactory/api/plugins/execute/getGavcBySha1?params=sha1=cf2171cba8bbbdf7f423f9ef54d8626e4011fd96
     */
    getGavcBySha1(version: '1.0', description: 'Returns GAVC by SHA1',
                  httpMethod: 'GET') { params ->
        String sha1 = getStringProperty(params, 'sha1', true)
        RepoPath artifact = searches.artifactsBySha1(sha1)?.first()
        // TODO check for more than one result
        // TODO handle artifacts which don't match the repo layout
        message = repositories.getLayoutInfo(artifact)
        status = 200
    }
}

private String getStringProperty(params, pName, mandatory) {
    def key = params[pName]
    def val = key == null ? null : key[0].toString()
    if (mandatory && val == null) cancelCleanup("$pName is mandatory paramater", null, 400)
    return val
}

def cancelCleanup(String message, Throwable cause, int errorLevel) {
    log.warn message
    throw new CancelException(message, cause, errorLevel)
}