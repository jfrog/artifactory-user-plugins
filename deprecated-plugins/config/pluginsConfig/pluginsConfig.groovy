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

import groovy.io.FileType
import groovy.json.JsonBuilder

executions {
  def artHome = ctx.artifactoryHome.etcDir

  //usage: curl -X GET http://localhost:8088/artifactory/api/plugins/execute/listPlugins
  listPlugins(httpMethod: 'GET') {
    def list = []
    def pluginsDir = new File(artHome, "plugins")

    pluginsDir.eachFile(FileType.FILES) { file ->
      if (file.getName() ==~ /[^\s]+(\.(?i)(groovy))$/) {
        list << file.getName()
      }
    }
    /* check if the list is empty */
    if (list.empty || list == null){
      message = "There are no user plugins installed in this Artifactory instance"
      status 204
    } else {
      message = new JsonBuilder(list).toPrettyString()
      status = 200
    }
  }

  //usage: curl -X GET "http://localhost:8088/artifactory/api/plugins/execute/downloadPlugin?params=name=<pluginName>"
  downloadPlugin(httpMethod: 'GET') { params->
    def pluginName = params?.('name')?.get(0) as String

    File pluginFile = new File("${artHome}/plugins/${pluginName}")
    if (!pluginName || !pluginFile.exists()) {
      message = "Error: ${pluginName} was not found"
      status = 400
    } else {
      message = pluginFile.text
      status = 200
    }
  }
}
