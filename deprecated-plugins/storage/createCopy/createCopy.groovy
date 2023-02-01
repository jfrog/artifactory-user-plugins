/*
 * Copyright (C) 2016 JFrog Ltd.
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
import org.artifactory.repo.RepoPathFactory

@Field final String PROPERTIES_FILE_PATH = 'plugins/createCopy.properties'

storage {
	def config = new ConfigSlurper().parse(new File(ctx.artifactoryHome.etcDir, PROPERTIES_FILE_PATH).toURL())
	repositoryList = config.repository
	repocopyList   = config.repocopy	

	afterCreate { item ->
		if ((item.repoPath.repoKey in repositoryList) && (item.repoPath.isFile())) { //if this is a repo in .properties, and the item is an artifact
			repocopy = repocopyList[repositoryList.indexOf(item.repoPath.repoKey)]     //repocopy value that corresponds to repository
			asSystem {
				toRepoPath = RepoPathFactory.create(repocopy, item.repoPath.path)
				try {
					repositories.copy(item.repoPath, toRepoPath)
					log.debug("Copied artifact '" + item.repoPath.path + "' to repository '" + repocopy + "'.")
				}
				catch (Exception e) {
					log.warn("Unable to copy artifact '" + item.repoPath.path + "' to repository '" + repocopy + "' : " + e)
				}
			}
		}
	}
}
