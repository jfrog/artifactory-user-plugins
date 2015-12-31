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
//@Grab('org.codehaus.groovy.modules.http-builder:http-builder:0.7' )
import groovyx.net.http.HTTPBuilder
import static groovyx.net.http.ContentType.TEXT
import static groovyx.net.http.Method.POST

import static WorkflowStatuses.*
import static com.google.common.collect.Multimaps.forMap

/**
 * Execute an asynchronous workflow each time a new file is saved
 * based on newFileWorkflow.groovy
 * @author markgalpin
 * @since Dec 30 2015
 */

enum WorkflowStatuses {
    NEW, // State of the artifact as it is created in Artifactory
    PENDING, // State of all new artifacts just before being send to the execute command
    PASSED, // State of artifacts already executed and test passed
    FAILED, // State of artifacts already executed and test failed
    FAILED_EXECUTION // State of artifacts where execute command failed

    static final WORKFLOW_STATUS_PROP_NAME = 'workflow.status'
    static final WORKFLOW_RESULT_PROP_NAME = 'workflow.error'
}

final String SERVICE_URL = 'http://localhost:8942/dummy/webapi/myresource'

boolean applyTo(ItemInfo item) {
    // Add the code to filter the kind of element the workflow applies to
    // Following Example: All non pom or metadata files saved in a local repository
    RepoPath repoPath = item.repoPath
    // Activate workflow only on actual local artifacts not pom or metadata
    !item.folder && //!isRemote(repoPath.repoKey) && //definitely want to do this on cache repos
        !MavenNaming.isMavenMetadata(repoPath.path) &&
        !MavenNaming.isPom(repoPath.path)
}

void dummyExecute(RepoPath repoPath) {
    String curlargs = "-X POST -H \"Content-Type: text/plain\" ";
    String url = "\""+SERVICE_URL+"\"";
    String post = " -d \"";
    String path = repoPath.toString().replaceAll("[:]", "/");
    String command = curlargs + url + post + path + "\""; 
    log.error("Workflow plugin calling API: "+command);

    def http = new HTTPBuilder( SERVICE_URL )
    http.request(POST, TEXT) {
        body = path
        response.success = {
            log.debug "Workflow Plugin successfully posted to API"
        }
    }
}

storage {
    afterCreate { ItemInfo item ->
        try {
            if (applyTo(item)) {
                setWorkflowStatus(item.repoPath, NEW)
                workflowExecute(item.repoPath)
            }
        } catch (Exception e) {
            log.error("Workflow plugin could not set property on $item", e)
        }
    }
}

/*  //For purposes of demo we did it on file upload.  For performance reasons an aggregation job like 
    //the original may be better  
    //Uncommenting this will not work out of the box.
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
                setWorkflowError(newArtifact, EXECUTED, result)
            } catch (Exception e) {
                log.debug("exception caught", e)
                setWorkflowError(newArtifact, FAILED_EXECUTION, e.getMessage())
            }
        }
    }
}
*/

private void workflowExecute(RepoPath newArtifact) {
    log.debug "Workflow plugin found artifact ${newArtifact.getName()} that needs work"
    setWorkflowStatus(newArtifact, PENDING)
    // Execute command
    try {
        dummyExecute(newArtifact)
    } catch (Exception e) {
        log.debug("Workflow plugin exception caught", e)
        setWorkflowError(newArtifact, FAILED_EXECUTION, e.getMessage())
    }
}

private void setWorkflowStatus(RepoPath repoPath, WorkflowStatuses status) {
    log.debug "Workflow plugin setting ${WORKFLOW_STATUS_PROP_NAME}=${status} on ${repoPath.getId()}"
    repositories.setProperty(repoPath, WORKFLOW_STATUS_PROP_NAME, status.name())
}

private void setWorkflowError(RepoPath repoPath, WorkflowStatuses status, String result) {
    setWorkflowStatus(repoPath, status)
    log.debug "Workflow plugin setting ${WORKFLOW_RESULT_PROP_NAME}=${result} on ${repoPath.getId()}"
    repositories.setProperty(repoPath, WORKFLOW_RESULT_PROP_NAME, result)
}

def isRemote(String repoKey) {
    if (repoKey.endsWith('-cache')) repoKey = repoKey.substring(0, repoKey.length() - 6)
    return repositories.getRemoteRepositories().contains(repoKey)
}

