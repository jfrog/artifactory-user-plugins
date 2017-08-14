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


import groovyx.net.http.HttpResponseException
import org.jfrog.artifactory.client.model.repository.settings.impl.GenericRepositorySettingsImpl
import spock.lang.Shared
import spock.lang.Specification

import static org.jfrog.artifactory.client.ArtifactoryClient.create

class CranTest extends Specification {

    static final baseurl = 'http://localhost:8088/artifactory'
    static final repoKey = 'cran-local'
    static final adminPassword = 'admin:password'
    static final INDEXING_STATUS_PROPERTY = 'cran.indexing.status'
    static final WAIT_INDEXING_TIMEOUT = 10000
    @Shared artifactory = create(baseurl, 'admin', 'password')

    /**
     * Runs before all tests
     */
    def setupSpec() {
        createLocalCranRepo(repoKey)
    }

    /**
     * Runs after all tests
     */
    def cleanupSpec() {
        artifactory.repository(repoKey)?.delete()
    }

    def 'source package indexing test'() {
        setup:
        def repo = artifactory.repository(repoKey)
        def packageFile = 'cats_1.0.1.tar.gz'
        def repoFolder = 'src/contrib'
        repo.upload(packageFile, new File("./src/test/groovy/CranTest/$packageFile")).doUpload()

        when:
        waitUntilIndexingIsCompleted(repo, repoFolder)

        then:
        // Check layout enforcement
        !fileExists(repo, packageFile)
        fileExists(repo, "$repoFolder/$packageFile")
        // Check root was not indexed
        !fileExists(repo, "PACKAGES")
        // Check index files were created
        fileExists(repo, "$repoFolder/PACKAGES")
        fileExists(repo, "$repoFolder/PACKAGES.gz")
        // Check if package was indexed
        checkLinesPresentInFile(repo, "$repoFolder/PACKAGES", '\n', ['Package: cats', 'Version: 1.0.1'])

        cleanup:
        repo.delete(repoFolder)
    }

    def 'macosx binary package indexing test'() {
        setup:
        def repo = artifactory.repository(repoKey)
        def packageFile = 'cats_1.0.0.tgz'
        def repoFolder = 'bin/macosx/el-capitan/contrib/3.4'
        repo.upload(packageFile, new File("./src/test/groovy/CranTest/$packageFile")).doUpload()

        when:
        waitUntilIndexingIsCompleted(repo, repoFolder)

        then:
        // Check layout enforcement
        !fileExists(repo, packageFile)
        fileExists(repo, "$repoFolder/$packageFile")
        // Check root was not indexed
        !fileExists(repo, "PACKAGES")
        // Check index files were created
        fileExists(repo, "$repoFolder/PACKAGES")
        fileExists(repo, "$repoFolder/PACKAGES.gz")
        // Check if package was indexed
        checkLinesPresentInFile(repo, "$repoFolder/PACKAGES", '\n', ['Package: cats', 'Version: 1.0.0'])

        cleanup:
        repo.delete(repoFolder)
    }

    def 'windows binary package indexing test'() {
        setup:
        def repo = artifactory.repository(repoKey)
        def packageFile = 'fortunes_1.5-4.zip'
        def repoFolder = 'bin/windows/contrib/3.4'
        repo.upload(packageFile, new File("./src/test/groovy/CranTest/$packageFile")).doUpload()

        when:
        waitUntilIndexingIsCompleted(repo, repoFolder)

        then:
        // Check layout enforcement
        !fileExists(repo, packageFile)
        fileExists(repo, "$repoFolder/$packageFile")
        // Check root was not indexed
        !fileExists(repo, "PACKAGES")
        // Check index files were created
        fileExists(repo, "$repoFolder/PACKAGES")
        fileExists(repo, "$repoFolder/PACKAGES.gz")
        // Check if package was indexed
        checkLinesPresentInFile(repo, "$repoFolder/PACKAGES", '\r\n', ['Package: fortunes', 'Version: 1.5-4'])

        cleanup:
        repo.delete(repoFolder)
    }

    def 'user defined package indexing test'() {
        setup:
        def repo = artifactory.repository(repoKey)
        def packageFile = 'cats_1.0.0.tgz'
        def repoFolder = 'bin/user-defined/contrib/3.4'
        repo.upload("$repoFolder/$packageFile", new File("./src/test/groovy/CranTest/$packageFile")).doUpload()

        when:
        waitUntilIndexingIsCompleted(repo, repoFolder)

        then:
        fileExists(repo, "$repoFolder/$packageFile")
        // Check index files were created
        fileExists(repo, "$repoFolder/PACKAGES")
        fileExists(repo, "$repoFolder/PACKAGES.gz")
        // Check if package was indexed
        checkLinesPresentInFile(repo, "$repoFolder/PACKAGES", '\n', ['Package: cats', 'Version: 1.0.0'])

        cleanup:
        repo.delete(repoFolder)
    }

    def 'unknown package deploy test'() {
        setup:
        def repo = artifactory.repository(repoKey)

        when:
        def packageFile = 'package.rar'
        repo.upload("$packageFile", new ByteArrayInputStream('data'.bytes)).doUpload()

        then:
        // Check package stay at location
        fileExists(repo, packageFile)
        // Wait for possible unwanted indexing execution
        sleep(7000)
        // Check index file DO NOT exist
        !fileExists(repo, "PACKAGES")

        cleanup:
        repo.delete(packageFile)
    }

    def 'package deletion indexing test'() {
        setup:
        def repo = artifactory.repository(repoKey)
        def packageFile = 'cats_1.0.0.tgz'
        def repoFolder = 'bin/user-defined/contrib/3.4'
        repo.upload("$repoFolder/$packageFile", new File("./src/test/groovy/CranTest/$packageFile")).doUpload()

        when:
        waitUntilIndexingIsCompleted(repo, repoFolder)
        repo.delete("$repoFolder/$packageFile")
        waitUntilIndexingIsCompleted(repo, repoFolder)

        then:
        // Check package was removed
        !fileExists(repo, "$repoFolder/$packageFile")
        // Check index files still exist
        fileExists(repo, "$repoFolder/PACKAGES")
        fileExists(repo, "$repoFolder/PACKAGES.gz")
        // Check if package was removed from index
        !checkLinesPresentInFile(repo, "$repoFolder/PACKAGES", '\n', ['Package: cats'])

        cleanup:
        repo.delete(repoFolder)
    }

    def 'package copy indexing test'() {
        setup:
        def repo = artifactory.repository(repoKey)
        def packageFile = 'cats_1.0.0.tgz'
        def repoFolder = 'bin/user-defined/contrib/3.4'
        def copyToFolder = 'bin/another-one/contrib/3.4'
        repo.upload("$repoFolder/$packageFile", new File("./src/test/groovy/CranTest/$packageFile")).doUpload()

        when:
        waitUntilIndexingIsCompleted(repo, repoFolder)
        repo.file("$repoFolder/$packageFile").copy(repoKey, "$copyToFolder/$packageFile")
        waitUntilIndexingIsCompleted(repo, copyToFolder)

        then:
        // Cehck if package exists in destination folder
        fileExists(repo, "$copyToFolder/$packageFile")
        // Check index files exist
        fileExists(repo, "$repoFolder/PACKAGES")
        fileExists(repo, "$repoFolder/PACKAGES.gz")
        fileExists(repo, "$copyToFolder/PACKAGES")
        fileExists(repo, "$copyToFolder/PACKAGES.gz")
        // Check if package was added to index of destination folder
        checkLinesPresentInFile(repo, "$copyToFolder/PACKAGES", '\n', ['Package: cats'])
        // Check if package still exists in index of origin folder
        checkLinesPresentInFile(repo, "$repoFolder/PACKAGES", '\n', ['Package: cats'])

        cleanup:
        repo.delete(copyToFolder)
        repo.delete(repoFolder)
    }

    def 'package move indexing test'() {
        setup:
        def repo = artifactory.repository(repoKey)
        def packageFile = 'cats_1.0.0.tgz'
        def repoFolder = 'bin/user-defined/contrib/3.4'
        def moveToFolder = 'bin/another-one/contrib/3.4'
        repo.upload("$repoFolder/$packageFile", new File("./src/test/groovy/CranTest/$packageFile")).doUpload()

        when:
        waitUntilIndexingIsCompleted(repo, repoFolder)
        repo.file("$repoFolder/$packageFile").move(repoKey, "$moveToFolder/$packageFile")
        waitUntilIndexingIsCompleted(repo, repoFolder)
        waitUntilIndexingIsCompleted(repo, moveToFolder)

        then:
        // Check if package was removed from origin
        !fileExists(repo, "$repoFolder/$packageFile")
        // Cehck if package exists in destination folder
        fileExists(repo, "$moveToFolder/$packageFile")
        // Check index files exist
        fileExists(repo, "$repoFolder/PACKAGES")
        fileExists(repo, "$repoFolder/PACKAGES.gz")
        fileExists(repo, "$moveToFolder/PACKAGES")
        fileExists(repo, "$moveToFolder/PACKAGES.gz")
        // Check if package was added to index of destination folder
        checkLinesPresentInFile(repo, "$moveToFolder/PACKAGES", '\n', ['Package: cats'])
        // Check if package was removed from index of origin folder
        !checkLinesPresentInFile(repo, "$repoFolder/PACKAGES", '\n', ['Package: cats'])

        cleanup:
        repo.delete(moveToFolder)
        repo.delete(repoFolder)
    }

    def 'manual indexing request test'() {
        setup:
        def repo = artifactory.repository(repoKey)
        def packageFile = 'cats_1.0.1.tar.gz'
        def repoFolder = 'src/contrib'
        repo.upload(packageFile, new File("./src/test/groovy/CranTest/$packageFile")).doUpload()

        when:
        waitUntilIndexingIsCompleted(repo, repoFolder)
        // Remove index file
        repo.delete("$repoFolder/PACKAGES")
        // Request indexing
        artifactory.plugins().execute('cranIndex')
                .withParameter('repoKey', repoKey)
                .withParameter('path', "$repoFolder")
                .sync()
        waitUntilIndexingIsCompleted(repo, repoFolder)

        then:
        // Check index files were created
        fileExists(repo, "$repoFolder/PACKAGES")
        fileExists(repo, "$repoFolder/PACKAGES.gz")
        // Check if package was indexed
        checkLinesPresentInFile(repo, "$repoFolder/PACKAGES", '\n', ['Package: cats', 'Version: 1.0.1'])

        cleanup:
        repo.delete(repoFolder)
    }

    /**
     * Create local CRAN. A CRAN repo is a generic repository with property 'cran' assigned to any value
     * @param repoKey - key of the repository
     */
    def createLocalCranRepo(String repoKey) {
        // Create repo
        def builder = artifactory.repositories().builders()
        def local = builder.localRepositoryBuilder().key(repoKey)
                .repositorySettings(new GenericRepositorySettingsImpl()).build()
        artifactory.repositories().create(0, local)
        // Set cran property
        "curl -X PUT -u$adminPassword $baseurl/api/storage/$repoKey?properties=cran=true".execute().waitFor()
    }

    /**
     * Wait until CRAN indexing is done. Will timeout after 10 seconds of wait.
     * @param repo - Repository to look for CRAN repo
     * @param path - CRAN repo path
     */
    def waitUntilIndexingIsCompleted(repo, path) {
        def status = null
        def initialTime = System.currentTimeMillis()
        while (status == null || status == 'scheduled' || status == 'running') {
            if (System.currentTimeMillis() - initialTime > WAIT_INDEXING_TIMEOUT) {
                throw new Exception("Repository $path indexing wait timed out after $WAIT_INDEXING_TIMEOUT millis")
            }
            sleep(1000)
            try {
                status = repo.folder(path).getPropertyValues(INDEXING_STATUS_PROPERTY)?.get(0)
            } catch (Exception e) {}
        }

        if (status != 'done') {
            throw new Exception("Repository $path indexing failed")
        }
    }

    /**
     * Check if file exists in repository
     * @param repo - Repository to look for artifact
     * @param path - Path to artifact
     * @return True if file exists in repository. False otherwise.
     */
    def fileExists(repo, path) {
        return getFileInfo(repo, path) != null
    }

    /**
     * Get file artifact info
     * @param repo - Repository to look for artifact
     * @param path - Path to artifact
     * @return Artifact info or null if artifact cannot be found
     */
    def getFileInfo(repo, path) {
        try {
            return repo.file(path).info()
        } catch (HttpResponseException e) {
            e.printStackTrace()
        }
        return null
    }

    /**
     * Check if lines is present in text file
     * @param repo - Repository to look for file
     * @param path - Path to the text file
     * @param lineSeparator: - Line separator string used in file
     * @param lines - Lines to be searched for
     * @return True if file contains all the lines in 'lines'. False otherwise.
     */
    def checkLinesPresentInFile(repo, path, lineSeparator, lines) {
        def fileContent = repo.download(path).doDownload()?.text
        if (!fileContent) {
            return false
        }
        def fileLines = fileContent.split(lineSeparator)
        for (fileLine in fileLines) {
            for (def i = 0; i < lines.size(); i++) {
                if (fileLine == lines[i]) {
                    lines.remove(i)
                    if (lines.isEmpty()) {
                        return true
                    } else {
                        i--
                    }
                }
            }
        }
        return false
    }
}