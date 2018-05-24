import org.apache.commons.codec.digest.DigestUtils
import spock.lang.Shared
import spock.lang.Specification

import org.jfrog.artifactory.client.model.repository.settings.impl.MavenRepositorySettingsImpl

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
class ChecksumsTest extends Specification {

    static final baseurl = 'http://localhost:8088/artifactory'
    static final message = 'Lorem ipsum dolor sit amet'
    static final artifactPath = 'testfile'
    static final repoKey = 'maven-local'
    @Shared artifactory = ArtifactoryClientBuilder.create().setUrl(baseurl).setUsername('admin').setPassword('password').build()

    def 'checksum test'() {
        setup:
        def repo = createLocalRepo(repoKey)
        def data = new ByteArrayInputStream(message.bytes)
        repo.upload(artifactPath, data).doUpload()

        when:
        def result256 = repo.download("${artifactPath}.sha256").doDownload().text
        def result384 = repo.download("${artifactPath}.sha384").doDownload().text
        def result512 = repo.download("${artifactPath}.sha512").doDownload().text

        then:
        // Validate sha256
        def checksum256 = DigestUtils.sha256Hex(message)
        checksum256 == result256
        repo.file(artifactPath).getPropertyValues('checksum.sha256') == null ||
            checksum256 == repo.file(artifactPath).getPropertyValues('checksum.sha256')[0]
        // Validate sha384
        def checksum384 = DigestUtils.sha384Hex(message)
        checksum384 == result384
        checksum384 == repo.file(artifactPath).getPropertyValues('checksum.sha384')[0]
        // Validate sha512
        def checksum512 = DigestUtils.sha512Hex(message)
        checksum512 == result512
        checksum512 == repo.file(artifactPath).getPropertyValues('checksum.sha512')[0]

        cleanup:
        artifactory.repository(repoKey)?.delete()
    }

    def createLocalRepo(String repoKey) {
        def builder = artifactory.repositories().builders()
        def local = builder.localRepositoryBuilder().key(repoKey)
                .repositorySettings(new MavenRepositorySettingsImpl()).build()
        artifactory.repositories().create(0, local)
        return artifactory.repository(repoKey)
    }
}
