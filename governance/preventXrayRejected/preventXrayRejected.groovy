/*
 * Copyright (C) 2016 JFrog Ltd.
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

// A set of severities to check for when rejecting a download. Edit this to suit
// your needs. Possible values are 'Minor', 'Major', and 'Critical'.
rejectSeverities = ['Minor', 'Major', 'Critical']

download {
    altResponse { request, responseRepoPath ->
        def props = repositories.getProperties(responseRepoPath)
        def severity = props.entries().find {
            it.key ==~ 'xray\\.[-a-zA-Z0-9]+\\.alert\\.topSeverity'
        }
        if (severity && severity.value in rejectSeverities) {
            message = "Download rejected: artifact $responseRepoPath is marked"
            message += " with an Xray alert of $severity.value severity."
            log.warn(message)
            status = 403
        }
    }
}
