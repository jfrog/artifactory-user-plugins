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

import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import org.artifactory.addon.p2.P2Repo
import org.artifactory.api.config.RepositoryConfigService
import org.artifactory.descriptor.repo.RepoType
import org.artifactory.model.VirtualRepoConfig
import org.artifactory.repo.RepoPathFactory
import org.artifactory.repo.packagetype.configuration.P2Configuration
import org.artifactory.resource.ResourceStreamHandle
import org.artifactory.ui.rest.model.admin.configuration.repository.virtual.VirtualRepositoryConfigModel
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.UpdateRepoConfigHelper
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.builder.RepoConfigModelBuilder
import org.artifactory.util.P2PackageTypeConfigMapper

/**
 * Test with
 * curl -uadmin:password -X POST "http://localhost:8081/artifactory/api/plugins/execute/getP2Urls?params=repo=repoKey"
 *
 * and
 * curl -uadmin:password -X POST --data-binary "{\"repo\": \"repoKey\",
 \"urls\": [ "", "" ]}" http://localhost:8080/artifactory/api/plugins/execute/modifyP2Urls
 *
 * @author Michal Reuven
 * @since 12/10/14
 */

/**
 ************************************************************************************
 * NOTE!!! This code makes use of non-advertized APIs, and may break in the future! *
 ************************************************************************************
 */

class P2UrlsConf {
    String repo
    String[] urls

    boolean isValid() {
        (repo && urls)
    }
}

executions {
    getP2Urls() { params ->
        def repoKey = params?.('repo')?.get(0) as String
        if (!repoKey) {
            def msg = "Mandatory parameter repo is missing."
            log.warn(msg)
            status = 400
            message = msg
            return
        }
        RepositoryConfigService repositoryConfigService = ctx.beanForType(RepositoryConfigService.class)
        status = 200
        def result = new P2UrlsConf()
        result.repo = repoKey
        P2Configuration p2Configuration = null;
        def repoConfig = repositoryConfigService.getRepoConfigByKey(repoKey)
        if (repoConfig) {
            p2Configuration = P2PackageTypeConfigMapper.INSTANCE.map(repoConfig.packageTypeConfig)
        }
        result.urls = p2Configuration?.urls?.toArray(new String[0])
        message = new JsonBuilder(result).toPrettyString()
    }

    // curl -uadmin:password -T /path/to/.json/file -X POST "http://localhost:8081/artifactory/api/plugins/execute/modifyP2Urls"
    modifyP2Urls() { ResourceStreamHandle body ->
        def updater = ctx.beanForType(UpdateRepoConfigHelper.class)
        def builder = ctx.beanForType(RepoConfigModelBuilder.class)
        assert body
        def json = new JsonSlurper().parse(new InputStreamReader(body.inputStream))
        def input = new P2UrlsConf()
        input.repo = json.repo as String
        input.urls = json.urls as String[]

        if (!input.isValid()) {
            def msg = "Mandatory parameters repo, urls are missing please provide them in your input JSON body"
            log.warn(msg)
            status = 400
            message = msg
            return
        }

        RepositoryConfigService repositoryConfigService = ctx.beanForType(RepositoryConfigService.class)
        VirtualRepoConfig virtualRepoConfig = repositoryConfigService.getVirtualRepoConfigByKey(input.repo)
        // check that the repo config is not NULL and that its a virtual repo. if not - return.
        if (!virtualRepoConfig || virtualRepoConfig.getPackageType() != RepoType.P2) {
            def msg = "The virtual repo ${input.repo} is not virtual or doesnt have a p2 configuration"
            log.warn(msg)
            status = 400
            message = msg
            return
        }

        def model = new VirtualRepositoryConfigModel()
        model = model.fromRepoConfig(builder, virtualRepoConfig)

        def p2Repos = Arrays.asList(input.urls).collect {
            def key = null
            if (it.startsWith('local://')) key = it - 'local://'
            else key = new URL(it).host
            new P2Repo(null, null, key, it)
        }

        model.typeSpecific.p2Repos = p2Repos
        model.updateRepo(updater)

        // refetch and push the config, to get rid of any invalid repos
        virtualRepoConfig = repositoryConfigService.getVirtualRepoConfigByKey(input.repo)
        model = new VirtualRepositoryConfigModel()
        model = model.fromRepoConfig(builder, virtualRepoConfig)
        model.updateRepo(updater)

        status = 201
        def result = new P2UrlsConf()
        result.repo = input.repo
        P2Configuration p2Configuration = null;
        def repoConfig = repositoryConfigService.getRepoConfigByKey(input.repo)
        if (repoConfig) {
            p2Configuration = P2PackageTypeConfigMapper.INSTANCE.map(repoConfig.packageTypeConfig)
        }
        result.urls = p2Configuration?.urls?.toArray(new String[0])
        message = new JsonBuilder(result).toPrettyString()

        // virtual repo needs to be cleaned
        def rootPath = RepoPathFactory.create(virtualRepoConfig.getKey(), "")
        repositories.deleteAtomic(rootPath)
    }
}
