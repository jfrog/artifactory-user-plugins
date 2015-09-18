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
import org.artifactory.fs.ItemInfo
import org.artifactory.mime.MavenNaming
import org.artifactory.repo.RepoPath

import static WorkflowStatuses.*
import static com.google.common.collect.Multimaps.forMap

/**
 * Execute a workflow each time a new file is saved
 *
 * @author Michal Reuven
 * @since 10/09/14
 */

/**
 ************************************************************************************
 * NOTE!!! This code makes use of non-advertized APIs, and may break in the future! *
 ************************************************************************************
 */

enum WorkflowStatuses {
    NEW, // State of the artifact as it is created in Artifactory
    PENDING, // State of all new artifacts just before being send to the execute command
    EXECUTED, // State of artifacts already executed and execute command returned correctly
    FAILED_EXECUTION // State of artifacts where execute command failed

    static final WORKFLOW_STATUS_PROP_NAME = 'workflow.status'
    static final WORKFLOW_RESULT_PROP_NAME = 'workflow.result'
}

boolean applyTo(ItemInfo item) {
    // Add the code to filter the kind of element the workflow applies to
    // Following Example: All non pom or metadata files saved in a local repository
    RepoPath repoPath = item.repoPath
    // Activate workflow only on actual local artifacts not pom or metadata
    !item.folder && !isRemote(repoPath.repoKey) &&
        !MavenNaming.isMavenMetadata(repoPath.path) &&
        !MavenNaming.isPom(repoPath.path)
}

String dummyExecute(RepoPath repoPath) {
    // Throw an exception if the file contains 'A' in it.

    // closeable.withCloseable(repositories.getContent(repoPath).inputStream) { InputStream is ->
    InputStream is = repositories.getContent(repoPath).inputStream
    try {
        def b = new byte[8096]
        int n
        while ((n = is.read(b)) != -1) {
            log.debug("executing dummyExecute.")
            for (int i = 0; i < n; i++) {
                if (b[i] == 'A') {
                    log.debug("if")
                    throw new CancelException("There is an A in your file", null, 500)
                }
            }
        }
    } finally {
        is.close()
    }
    "OK"
}

storage {
    afterCreate { ItemInfo item ->
        try {
            if (applyTo(item)) {
                setWorkflowStatus(item.repoPath, NEW)
            }
        } catch (Exception e) {
            log.error("Could not set property on $item", e)
        }
    }
}

jobs {
    // Activate workflow every 5 minutes on all new (or other state) item to do whatever!
    activateWorkflow(interval: 20000, delay: 2000) {
        def filter = [:]
        filter.put(WORKFLOW_STATUS_PROP_NAME, NEW.name())
        List<RepoPath> paths = searches.itemsByProperties(forMap(filter))
        paths.each { RepoPath newArtifact ->
            log.debug "Found artifact ${newArtifact.getName()} that needs work"
            setWorkflowStatus(newArtifact, PENDING)
            // Execute command
            try {
                def result = dummyExecute(newArtifact)
                setWorkflowResult(newArtifact, EXECUTED, result)
            } catch (Exception e) {
                log.debug("exception caught", e)
                setWorkflowResult(newArtifact, FAILED_EXECUTION, e.getMessage())
            }
        }
    }
}

private void setWorkflowStatus(RepoPath repoPath, WorkflowStatuses status) {
    log.debug "Setting ${WORKFLOW_STATUS_PROP_NAME}=${status} on ${repoPath.getId()}"
    repositories.setProperty(repoPath, WORKFLOW_STATUS_PROP_NAME, status.name())
}

private void setWorkflowResult(RepoPath repoPath, WorkflowStatuses status, String result) {
    setWorkflowStatus(repoPath, status)
    log.debug "Setting ${WORKFLOW_RESULT_PROP_NAME}=${result} on ${repoPath.getId()}"
    repositories.setProperty(repoPath, WORKFLOW_RESULT_PROP_NAME, result)
}

def isRemote(String repoKey) {
    if (repoKey.endsWith('-cache')) repoKey = repoKey.substring(0, repoKey.length() - 6)
    return repositories.getRemoteRepositories().contains(repoKey)
}
