import org.jfrog.artifactory.client.Artifactory
import org.jfrog.artifactory.client.impl.RepositoryHandleImpl
import org.jfrog.artifactory.client.model.Item
import org.jfrog.artifactory.client.model.repository.settings.impl.DebianRepositorySettingsImpl
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
class ExpirePackagesMetadataTest extends Specification {

    static final baseUrl = 'http://localhost:8088/artifactory'
    static final remoteRepoKey = 'debian-remote'
    static final virtualRepoKey = 'debian-virtual'
    static final packagesPath = 'dists/xenial/main/binary-i386/Packages.gz'

    def 'Packages.gz not expired download test'() {
        setup:
        def artifactory = ArtifactoryClientBuilder.create().setUrl(baseUrl)
            .setUsername('admin').setPassword('password').build()
        def builder = artifactory.repositories().builders()
        def source = builder.localRepositoryBuilder().key('source-local')
        source.repositorySettings(new DebianRepositorySettingsImpl())
        artifactory.repositories().create(0, source.build())
        def sourcerepo = artifactory.repository('source-local')
        def pkgs = new File('./src/test/groovy/ExpirePackagesMetadataTest/Packages.gz')
        sourcerepo.upload(packagesPath, pkgs).doUpload();
        def remote = createRemoteDebianRepo(artifactory, remoteRepoKey)
        def virtual = createVirtualRepo(artifactory, virtualRepoKey, remoteRepoKey)

        when:
        // Perform first download request
        def infoFirstDownloadRequest =  downloadAndGetInfo(remote, virtual, packagesPath)
        // Perform second download request
        def infoSecondDownloadRequest = downloadAndGetInfo(remote, virtual, packagesPath)

        then:
        /*
         * Having the same last updated time indicates that the file was not fetched
         * from the remote repository between download requests
         */
        infoSecondDownloadRequest.lastUpdated == infoFirstDownloadRequest.lastUpdated

        cleanup:
        sourcerepo?.delete()
        remote?.delete()
        virtual?.delete()
    }

    def 'Packages.gz expired download test'() {
        setup:
        def artifactory = ArtifactoryClientBuilder.create().setUrl(baseUrl)
            .setUsername('admin').setPassword('password').build()
        def builder = artifactory.repositories().builders()
        def source = builder.localRepositoryBuilder().key('source-local')
        source.repositorySettings(new DebianRepositorySettingsImpl())
        artifactory.repositories().create(0, source.build())
        def sourcerepo = artifactory.repository('source-local')
        def pkgs = new File('./src/test/groovy/ExpirePackagesMetadataTest/Packages.gz')
        sourcerepo.upload(packagesPath, pkgs).doUpload();
        def remote = createRemoteDebianRepo(artifactory, remoteRepoKey)
        def virtual = createVirtualRepo(artifactory, virtualRepoKey, remoteRepoKey)
        // Set packages.gz cache time to 1 second
        setPackageMetadataCacheTime(artifactory, 1);

        when:
        // Perform first download request
        def infoFirstDownloadRequest =  downloadAndGetInfo(remote, virtual, packagesPath)
        // wait 2 seconds so the cache can have time to expire
        sleep(2000l)
        // Perform second download request
        def infoSecondDownloadRequest = downloadAndGetInfo(remote, virtual, packagesPath)

        then:
        /*
         * Last updated time after the second download request must be bigger
         * than after the first download, indicating that the remote artifact was fetched again
         * after the cache time has expired
         */
        infoSecondDownloadRequest.lastUpdated > infoFirstDownloadRequest.lastUpdated

        cleanup:
        sourcerepo?.delete()
        remote?.delete()
        virtual?.delete()
        // Set packages.gz cache time back to the default value
        setPackageMetadataCacheTime(artifactory, 1800)
    }

    private RepositoryHandleImpl createRemoteDebianRepo(Artifactory artifactory, String key) {
        def remoteBuilder = artifactory.repositories().builders().remoteRepositoryBuilder()
        remoteBuilder.key(key)
        remoteBuilder.repositorySettings(new DebianRepositorySettingsImpl())
        remoteBuilder.url('http://localhost:8088/artifactory/source-local').username('admin').password('password')
        artifactory.repositories().create(0, remoteBuilder.build())
        return artifactory.repository(key)
    }

    private RepositoryHandleImpl createVirtualRepo(Artifactory artifactory, String key, String includedRepo) {
        def virtualBuilder = artifactory.repositories().builders().virtualRepositoryBuilder().key(key)
        virtualBuilder.repositories([includedRepo])
        artifactory.repositories().create(0, virtualBuilder.build())
        return artifactory.repository(key)
    }

    private void setPackageMetadataCacheTime(Artifactory artifactory, long time) {
        artifactory.plugins().execute('setPackageMetadataCacheTime')
                .withParameter('cache_millis', time as String).sync()
    }

    private Item downloadAndGetInfo(RepositoryHandleImpl remote, RepositoryHandleImpl virtual, String path) {
        remote.download(path).doDownload().text
        virtual.file(path).info()
    }
}
