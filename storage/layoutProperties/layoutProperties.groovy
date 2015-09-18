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

import groovy.transform.Field
import org.artifactory.fs.FileLayoutInfo
import org.artifactory.fs.ItemInfo
import org.artifactory.repo.RepoPath

@Field final String PROPERTY_PREFIX = 'layout.'

/**
 * This is a simple plug-in that takes every token in a layout, both built-in and custom, and creates properties
 * from them.  We use the PROPERTY_PREFIX for each property so by default the properties will be layout.baseRevision
 * or layout.customTokenHere with the values as strings.
 */
storage {
    afterCreate { ItemInfo item ->
        RepoPath repoPath = item.repoPath
        // Gets the full path of the artifact, including the repo
        FileLayoutInfo currentLayout = repositories.getLayoutInfo(repoPath)
        // Gets the actual layout of the repository the artifact is deployed to
        if (currentLayout.isValid()) {
            try {
                ['organization', 'module', 'baseRevision', 'folderIntegrationRevision', 'fileIntegrationRevision', 'classifier', 'ext', 'type'].each { String propName ->
                    repositories.setProperty(repoPath, PROPERTY_PREFIX + propName, currentLayout."$propName" as String)
                } // This pulls all the default tokens
                def customFields = currentLayout.getCustomFields()
                if (customFields) {
                    Set<String> customProps = customFields.keySet()
                    customProps.each { String propName -> repositories.setProperty(repoPath, PROPERTY_PREFIX + propName, currentLayout.getCustomField("$propName")) }
                    // pulls the custom tokens
                }
            } catch (Exception ex) {
                log.error("Could not set properties on ${repoPath}", ex)
            }
        } else {
            log.warn("Skipping property sets for $item - layout invalid")
        }
    }
}
