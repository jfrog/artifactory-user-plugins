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

/**
 *
 * @author Michal Reuven
 * @since 12/10/14
 */


import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import org.artifactory.descriptor.config.CentralConfigDescriptor
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor
import org.artifactory.descriptor.repo.P2Configuration
import org.artifactory.descriptor.repo.RepoLayout
import org.artifactory.descriptor.repo.RepoType
import org.artifactory.repo.RepoPathFactory
import org.artifactory.resource.ResourceStreamHandle


/**
 ************************************************************************************
 * NOTE!!! This code makes use of non-advertized APIs, and may break in the future! *
 ************************************************************************************
 */

/**
 * Test with
 * curl -uadmin:password -X POST "http://localhost:8081/artifactory/api/plugins/execute/getP2Urls?params=repo=repoKey"
 *
 * and
 * curl -uadmin:password -X POST --data-binary "{
 \"repo\": \"repoKey\",
 \"urls\": [ "", "" ]
 }" http://localhost:8080/artifactory/api/plugins/execute/modifyP2Urls
 *
 */

class P2UrlsConf{
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
        CentralConfigDescriptor config = ctx.centralConfig.descriptor
        status = 200
        def result = new P2UrlsConf()
        result.repo = repoKey
        result.urls = config.getVirtualRepositoriesMap()?.get(repoKey)?.p2?.urls?.toArray(new String[0])
        message = new JsonBuilder(result).toPrettyString()
    }

    //curl -uadmin:password -T /path/to/.json/file -X POST "http://localhost:8081/artifactory/api/plugins/execute/modifyP2Urls"
    modifyP2Urls() { ResourceStreamHandle body ->
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

        def centralConfigService = ctx.centralConfig
        MutableCentralConfigDescriptor config = centralConfigService.mutableDescriptor
        def repoDescriptor = config.getVirtualRepositoriesMap()?.get(input.repo)
        // check that the repo descriptor is not NULL and that its a virtual repo. if not - return.
        if (!repoDescriptor || repoDescriptor.getP2() == null || !repoDescriptor.getType().equals(RepoType.P2)) {
            def msg = "The virtual repo ${input.repo} is not virtual or doesnt have a p2 configuration"
            log.warn(msg)
            status = 400
            message = msg
            return
        }

        P2Configuration p2Conf = repoDescriptor?.p2
        if (!p2Conf) {
            def msg = "The virtual repo ${input.repo} doesnt have a P2 configuration"
            log.warn(msg)
            status = 400
            message = msg
            return
        }
        p2Conf.setUrls(Arrays.asList(input.urls))
        repoDescriptor.setP2(p2Conf)
        config.getVirtualRepositoriesMap().put(repoDescriptor.key, repoDescriptor)

        centralConfigService.saveEditedDescriptorAndReload(config)
        status = 201
        message = new JsonBuilder(p2Conf).toPrettyString()

        //virtual repo needs to be cleand
        def rootPath = RepoPathFactory.create(repoDescriptor.getKey(), "")
        repositories.delete(rootPath)
    }
}
