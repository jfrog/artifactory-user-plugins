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

import org.artifactory.repo.RepoPath
import org.artifactory.repo.RepoPathFactory
import org.artifactory.repo.service.InternalRepositoryService
import org.artifactory.storage.binstore.service.BinaryStore
import org.artifactory.storage.db.DbService
import org.artifactory.storage.db.util.JdbcHelper

import static ConstantsData.SAVED_LOCAL_REPO

class ConstantsData {
    public static final String VIRTUAL_REPO = 'fmw-virtual'
    public static final String SAVED_LOCAL_REPO = 'saved-poms'
}

import static ConstantsData.VIRTUAL_REPO

/**
 *
 * @author Michal
 * @since 9/17/2014
 */

jobs {
    // Create the pending CC requests for all NEW artifacts
    copyModifiedPoms(interval: 300000, delay: 300000) {
        def jdbcHelper = ctx.beanForType(JdbcHelper.class)
        def repositoryService = ctx.beanForType(InternalRepositoryService.class)
        def binStore = ctx.beanForType(BinaryStore.class)
        def dbService = ctx.beanForType(DbService.class)

        findAndSaveAll(jdbcHelper, repositoryService, binStore, dbService)
    }
}

executions {
    testCopyModifiedPoms {
        def jdbcHelper = ctx.beanForType(JdbcHelper.class)
        def repositoryService = ctx.beanForType(InternalRepositoryService.class)
        def binStore = ctx.beanForType(BinaryStore.class)
        def dbService = ctx.beanForType(DbService.class)

        message = findAndSaveAll(jdbcHelper, repositoryService, binStore, dbService)
    }
}

def findAndSaveAll(JdbcHelper jdbcHelper, InternalRepositoryService repoService, BinaryStore binStore, DbService dbService) {
    def res = jdbcHelper.executeSelect("SELECT nv.repo, nv.node_path, nv.node_name, nv.sha1_actual, nl.repo, nl.sha1_actual " +
        " FROM nodes nv INNER JOIN nodes nl \n" +
        "ON nv.node_path = nl.node_path AND nv.node_name = nl.node_name \n" +
        "WHERE nv.repo=? AND nl.repo != ? AND nl.repo != ? \n" +
        "AND NOT EXISTS (SELECT ns.node_id from nodes ns\n" +
        "        WHERE ns.repo = ?\n" +
        "        AND ns.node_path = nv.node_path\n" +
        "        AND ns.node_name = nv.node_name\n" +
        "        AND ns.sha1_actual = nv.sha1_actual ) \n" +
        "AND nv.node_name like '%.pom' AND nv.sha1_actual != nl.sha1_actual",
        VIRTUAL_REPO, VIRTUAL_REPO, SAVED_LOCAL_REPO, SAVED_LOCAL_REPO)

    def saveRepo = repoService.storingRepositoryByKey(SAVED_LOCAL_REPO)
    def results = new StringBuilder()
    while (res.next()) {
        String path = "${res.getString(2)}/${res.getString(3)}".toString()
        String sha1 = res.getString(4)
        String originalRepoKey = res.getString(5)
        def result = "${res.getString(1)} $path $sha1 $originalRepoKey ${res.getString(6)}"
        println result
        results.append(result).append("\n")

        // TODO: Copy the properties from the original local repo
        RepoPath originalRepoPath = RepoPathFactory.create(originalRepoKey, path)
        def properties = repositories.getProperties(originalRepoPath)

        dbService.invokeInTransaction(
            { ->
                RepoPath savedRepoPath = RepoPathFactory.create(SAVED_LOCAL_REPO, path)
                def newFile = saveRepo.createOrGetFile(savedRepoPath)
                def binary = binStore.getBinary(sha1)
                newFile.fillBinaryData(binary)
                newFile.setProperties(properties)
                newFile.save()
            }
        )
    }
    results.toString()
}
