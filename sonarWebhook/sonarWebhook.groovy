/*
 * Copyright (C) 2019 JFrog Ltd.
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

import groovy.json.JsonSlurper
import java.lang.reflect.Array
import java.util.concurrent.Executors

import org.artifactory.api.build.BuildService
import org.artifactory.build.BuildRun
import org.artifactory.build.DetailedBuildRun
import org.artifactory.concurrent.ArtifactoryRunnable
import org.artifactory.exception.CancelException
import org.artifactory.repo.RepoPathFactory
import org.artifactory.resource.ResourceStreamHandle
import org.artifactory.search.Searches
import org.artifactory.search.aql.AqlResult
import org.jfrog.build.api.release.Promotion

import org.springframework.security.core.context.SecurityContextHolder

threadPool = Executors.newFixedThreadPool(10)

executions {
    updateSonarTaskStatus() { params, ResourceStreamHandle body ->
        def targetRepo = params?.getAt('targetRepo')?.getAt(0) ?: null
        def sourceRepo = params?.getAt('sourceRepo')?.getAt(0) ?: null
        def rolledBackRepo = params?.getAt('rolledBackRepo')?.getAt(0) ?: null
        def includeDep = new Boolean(params?.getAt('includeDep')?.getAt(0))
        def bodyJson = new JsonSlurper().parse(body.inputStream)
        def buildService = ctx.beanForType(BuildService.class)
        //Get build object based on task id from json object
        log.info("Processing SonarQube webhook for taskId $bodyJson.taskId")
        Collection<String> propValues = [bodyJson.qualityGate.status]
        Map<String, Collection<String>> props = new HashMap<String,Collection<String>>()
        props.put("SONARQUBE_QUALITY_GATE", propValues)
        bodyJson.qualityGate.conditions.each { condition ->
            props.put(condition.metric, [condition.status])
        }
        def task = [run:{
            def retry = 5
            def exists = false
            def aql = "builds.find({\"@buildInfo.env.SONAR_CETASKID\":\"$bodyJson.taskId\"})"
            while (!exists && retry > 0) {
                sleep(5000)
                searches.aql(aql.toString()) { AqlResult result ->
                    result.each { b ->
                        exists = true
                        log.warn "create promotion object"
                        def promotion = new Promotion()
                        promotion.ciUser = 'admin'
                        promotion.timestamp = '2018-02-11T18:30:24.825+0200'
                        promotion.dryRun = false
                        promotion.sourceRepo = sourceRepo
                        promotion.copy = false
                        promotion.artifacts = true
                        promotion.dependencies = true
                        promotion.scopes = [] as Set<String>
                        promotion.properties = props
                        promotion.failFast = true
                        //check quality gate result
                        if (bodyJson.qualityGate.status == 'OK') {
                            log.info("SonarQube quality gate ${bodyJson.qualityGate.name} passed for build ${b['build.name']}")
                            promotion.status = "STAGING"
                            promotion.comment = "Sonar quality gate successful"
                            promotion.targetRepo = targetRepo
                        } else {
                            log.warn("SonarQube quality gate ${bodyJson.qualityGate.name} failed for build ${b['build.name']}")
                            promotion.status = "ROLLED-BACK"
                            promotion.comment = "Sonar quality gate failed"
                            promotion.targetRepo = rolledBackRepo
                        }
                        def buildRun = builds.getBuilds(b['build.name'], b['build.number'], null)[0]
                        buildService.promoteBuild(builds.getDetailedBuild(buildRun), promotion)
                    }
                }
                retry--
            }
            if (exists) {
                log.warn("promotion done")
            } else {
                log.warn("build not found")
            }
        }] as Runnable
        def authctx = SecurityContextHolder.context.authentication
        threadPool.submit(new ArtifactoryRunnable(task, ctx, authctx))
    }
}
