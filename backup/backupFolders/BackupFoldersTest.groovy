import groovy.json.JsonBuilder
import org.jfrog.artifactory.client.model.repository.settings.impl.GenericRepositorySettingsImpl
import org.jfrog.lilypad.Control
import spock.lang.Specification

import org.jfrog.artifactory.client.ArtifactoryClientBuilder

/*
 * Copyright (C) 2017 JFrog Ltd.
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
class BackupFoldersTest extends Specification {
    static final basePort = 8088
    static final baseurl = "http://localhost:$basePort/artifactory"
    static final user = 'admin'
    static final password = 'password'
    static final userPassword = "$user:$password"
    static final auth = "Basic ${userPassword.bytes.encodeBase64()}"

    def 'Backup full repository test'() {

        def repoKey = 'repo-generic-local'
        def artifact1Path = 'path/to/txt/file.txt'
        def artifact2Path = 'path/to/sh/file.sh'
        def artifact1Content = 'txt content'
        def artifact2Content = 'sh content'
        def backupFolder = "/tmp/backupfoldertest/${System.currentTimeMillis()}"

        setup:
        def artifactory = ArtifactoryClientBuilder.create().setUrl(baseurl).setUsername(user).setPassword(password).build()
        // Create temporary folder to receive backup
        Control.createFolder(basePort, backupFolder)
        // Create repository
        def repo = createLocalRepo(artifactory, repoKey)
        // Create artifacts
        uploadFile(repo, artifact1Path, artifact1Content)
        uploadFile(repo, artifact2Path, artifact2Content)

        when:
        requestBackup(repoKey, backupFolder)

        then:
        isFilePresentInBackup(backupFolder, repoKey, artifact1Path)
        isFileContentEqualTo(backupFolder, repoKey, artifact1Path, artifact1Content)
        isFilePresentInBackup(backupFolder, repoKey, artifact2Path)
        isFileContentEqualTo(backupFolder, repoKey, artifact2Path, artifact2Content)

        cleanup:
        artifactory.repository(repoKey)?.delete()
        Control.deleteFolder(basePort, backupFolder)
    }

    def 'Backup partial repository test'() {

        def repoKey = 'repo-generic-local'
        def artifact1Path = 'path/to/txts/version/file.txt'
        def artifact2Path = 'path/to/shs/version/file.sh'
        def artifact1Content = 'txt content'
        def artifact2Content = 'sh content'
        def backupFolder = "/tmp/backupfoldertest/${System.currentTimeMillis()}"

        setup:
        def artifactory = ArtifactoryClientBuilder.create().setUrl(baseurl).setUsername(user).setPassword(password).build()
        // Create temporary folder to receive backup
        Control.createFolder(basePort, backupFolder)
        // Create repository
        def repo = createLocalRepo(artifactory, repoKey)
        // Create artifacts
        uploadFile(repo, artifact1Path, artifact1Content)
        uploadFile(repo, artifact2Path, artifact2Content)

        when:
        requestBackup("$repoKey/path/to/txts", backupFolder)

        then:
        isFilePresentInBackup(backupFolder, repoKey, artifact1Path)
        isFileContentEqualTo(backupFolder, repoKey, artifact1Path, artifact1Content)
        !isFilePresentInBackup(backupFolder, repoKey, artifact2Path)

        cleanup:
        artifactory.repository(repoKey)?.delete()
        Control.deleteFolder(basePort, backupFolder)
    }

    def createLocalRepo(artifactory, key) {
        def builder = artifactory.repositories().builders().localRepositoryBuilder()
                .key(key)
                .repositorySettings(new GenericRepositorySettingsImpl())
        artifactory.repositories().create(0, builder.build())
        return artifactory.repository(key)
    }

    def uploadFile(repo, path, content) {
        repo.upload(path, new ByteArrayInputStream(content.getBytes('UTF-8')))
                .doUpload()
    }

    def requestBackup(String path, String destinationFolder) {

        def json = [
                destinationFolder: destinationFolder,
                pathToFolder: path
        ]

        def conn = new URL("$baseurl/api/plugins/execute/backup").openConnection()
        conn.doOutput = true
        conn.requestMethod = 'POST'
        conn.setRequestProperty('Authorization', auth)
        conn.setRequestProperty('Content-Type', 'application/json')
        conn.outputStream.write(new JsonBuilder(json).toString().bytes)
        assert conn.responseCode == 200
        conn.disconnect()
    }

    def isFilePresentInBackup(backupFolder, repoKey, path) {
        def backupFolderContent = Control.getFolderContents(basePort, backupFolder)
        def filePath = "$backupFolder/${backupFolderContent[0]}/$repoKey/$path"
        return Control.fileExists(basePort, filePath)
    }

    def isFileContentEqualTo(backupFolder, repoKey, path, content) {
        def backupFolderContent = Control.getFolderContents(basePort, backupFolder)
        def filePath = "$backupFolder/${backupFolderContent[0]}/$repoKey/$path"
        return Control.getFileContent(basePort, filePath) == content
    }
}
