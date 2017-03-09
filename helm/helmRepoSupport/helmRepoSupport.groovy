/*
 * Copyright (C) 2015 JFrog Ltd.
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


import com.esotericsoftware.yamlbeans.YamlReader
import com.esotericsoftware.yamlbeans.YamlWriter
import org.artifactory.repo.service.InternalRepositoryService
import org.artifactory.request.RequestThreadLocal
import org.artifactory.util.HttpUtils

download {
    altRemoteContent { repoPath ->
        // don't modify the content unless we're dealing with a index.yaml
        if (!(repoPath.path ==~ '^(?:.+/)?index\\.yaml$')) {
            return
        }
        def pathmatch = repoPath.path =~ '^(.+/)?index\\.yaml$'
        def pathpre = pathmatch[0][1] ?: ''
        def repoService = ctx.beanForType(InternalRepositoryService)
        def repo = repoService.remoteRepositoryByKey(repoPath.repoKey)
        def url = ctx.centralConfig.descriptor.urlBase
        if (!url) {
            def req = RequestThreadLocal.context.get().requestThreadLocal
            url = HttpUtils.getServletContextUrl(req.request)
        }
        if (!url.endsWith("/")) {
            url += "/";
        }
        def streamhandle = null
        try {
            // open a stream to the remote and parse the repomd.xml file
            log.info("Downloading and parsing index.yaml")
            streamhandle = repo.downloadResource(repoPath.path)

            YamlReader reader = new YamlReader(streamhandle.inputStream.text);
            Object object = reader.read();
            Map map = (Map) object;
            def charts = map.get("entries");

            charts.each { k, v ->
                v.each { version ->
                    version.urls[0] = version.urls[0].replace("https://kubernetes-charts.storage.googleapis.com",
                            url + repo.key);
                }
            }

            def stringWrite = new StringWriter();
            YamlWriter writer = new YamlWriter(stringWrite);
            writer.write(object);
            writer.close();
            def stringWriteStr = stringWrite.toString()
            def bytes = stringWriteStr.bytes
            inputStream = new ByteArrayInputStream(bytes);
            size = bytes.length;
        } catch (Exception e) {
            System.out.println(e);
        }
        finally {
            // close the input stream
            streamhandle?.close()
        }
    }
}
