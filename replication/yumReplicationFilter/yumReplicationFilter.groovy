/*
 * Copyright (C) 2015 JFrog Ltd.
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

replication {
    beforeFileReplication { localRepoPath ->
        if ((localRepoPath.getName().endsWith(".xml.gz") ||
                localRepoPath.getName().equals("repomd.xml")) &&
                (localRepoPath.getPath().contains("/repodata/") ||
                        localRepoPath.getPath().startsWith("repodata/"))) {
            log.info("Skipping replication of a file:" +
                    " ${localRepoPath.getPath()} as it is a YUM metadata file")
            skip = true
        }
    }
    beforeDeleteReplication { localRepoPath ->
        if ((localRepoPath.getName().endsWith(".xml.gz") ||
                localRepoPath.getName().equals("repomd.xml")) &&
                (localRepoPath.getPath().contains("/repodata/") ||
                        localRepoPath.getPath().startsWith("repodata/"))) {
            log.info("Skipping replication of delete a file:" +
                    " ${localRepoPath.getPath()} as it is a YUM metadata file")
            skip = true
        }
    }
}
