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
import org.codehaus.mojo.versions.ordering.VersionComparator
import org.codehaus.mojo.versions.ordering.VersionComparators
@Grapes([
    @Grab(group = 'org.semver', module = 'api', version = '0.9.20'),
    @Grab(group = 'org.semver', module = 'api', classifier = 'sources', version = '0.9.20'),
    @GrabExclude('asm:asm'),
    @GrabExclude('asm:asm-tree'),
    @GrabExclude('asm:asm-commons'),
    @GrabExclude('commons-lang:commons-lang')
])
import org.semver.Comparer
import org.semver.Delta

import java.nio.file.Path

import static java.nio.file.Files.*
import static org.semver.Delta.CompatibilityType.BACKWARD_COMPATIBLE_USER

@Field final String COMPATIBLE_PROPERTY_NAME = 'approve.binaryCompatibleWith'
@Field final String INCOMPATIBLE_PROPERTY_NAME = 'approve.binaryIncompatibleWith'

/**
 * This plugin performs binary compatibility checks when artifact is created in
 * Artifactory. Property binaryCompatibleWith or binaryIncompatibleWith will be
 * added with the version of artifact the check was performed against as a value.
 */

storage {
    afterCreate { ItemInfo item ->
        FileLayoutInfo currentLayout = repositories.getLayoutInfo(item.repoPath)
        if (currentLayout.organization) {
            if (currentLayout.ext == 'jar') {
                List<RepoPath> allVersions = searches.artifactsByGavc(currentLayout.getOrganization(),
                    currentLayout.getModule(), null, null).findAll { it.path.endsWith('.jar') }
                if (allVersions.size() > 1) {
                    VersionComparator mavenComparator = VersionComparators.getVersionComparator('mercury')
                    allVersions.sort(true, mavenComparator)
                    if (allVersions.pop().equals(item.repoPath)) {
                        RepoPath previousVersion = allVersions.pop()
                        FileLayoutInfo previousLayout = repositories.getLayoutInfo(previousVersion)
                        Path currentTempFile = createTempFile("${currentLayout.module}-current", '.jar')
                        Path previousTempFile = createTempFile("${previousLayout.module}-previous", '.jar')
                        try {
                            newOutputStream(currentTempFile) << repositories.getContent(item.repoPath).inputStream
                            newOutputStream(previousTempFile) << repositories.getContent(previousVersion).inputStream

                            Delta delta = new Comparer(previousTempFile.toFile(), currentTempFile.toFile(), [] as Set,
                                [] as Set).diff()
                            boolean compatible = delta.computeCompatibilityType().compareTo(
                                BACKWARD_COMPATIBLE_USER) > 0
                            repositories.setProperty(item.repoPath,
                                compatible ? COMPATIBLE_PROPERTY_NAME : INCOMPATIBLE_PROPERTY_NAME,
                                previousLayout.baseRevision)
                        } finally {
                            try {
                                delete(currentTempFile)
                            } catch (deleteFailed) {
                                log.warn('Failed to delete temp files', deleteFailed)
                            }
                            try {
                                delete(previousTempFile)
                            } catch (deleteFailed) {
                                log.warn('Failed to delete temp files', deleteFailed)
                            }
                        }
                    } else {
                        // TODO you might want to check versus previous or next version?
                        log.warn("Skipping compatibility analysis for $item.repoPath - newer versions already exist")
                    }
                } else {
                    log.debug("Skipping compatibility analysis for $item.repoPath - first version")
                }
            } else {
                log.debug("Skipping compatibility analysis for $item.repoPath - not jar")
            }
        } else {
            log.warn("Skipping compatibility analysis for $item - not in Maven layout")
        }
    }
}
