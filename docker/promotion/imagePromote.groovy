import org.artifactory.build.*
import org.artifactory.repo.RepoPath

import static com.google.common.collect.Multimaps.forMap

@Grab('org.codehaus.groovy.modules.http-builder:http-builder:0.7' )

import groovyx.net.http.HTTPBuilder
import static groovyx.net.http.Method.POST
import static groovyx.net.http.ContentType.*

promotions {
    promoteDocker(users: "admin", params: [targetRepository: 'docker-prod-local', status: 'Released', comment: 'Promoting Docker build']) { buildName, buildNumber, params ->
      log.warn("params: $params")
      log.warn "promoting $buildName, $buildNumber"
        def (name,version) = searches.itemsByProperties(forMap(['build.name': buildName,'build.number': buildNumber]))[0].path.split('/')
        log.warn "found image $name/$version"

          def http = new HTTPBuilder( 'http://private-docker:8081/artifactory/api/docker/docker-dev-local/v2/' )
          http.auth.basic('admin', 'password')

          http.request( POST, TEXT ) { req ->
            //repoKey\":\"docker-dev-local\",\"targetRepo\":\"docker-prod-local\",\"dockerRepository\":\"${name}\",\"tag\":\"${version}\"
              requestContentType = JSON
              body = [repoKey:'docker-dev-local', targetRepo:(params['targetRepository'])[0], dockerRepository:name, tag:version]
             response.success = { resp, json ->
               def buildRun = builds.getBuilds(buildName, buildNumber, null)[0]
               log.warn "found build $buildRun"
               def build = builds.getDetailedBuild(buildRun)
               log.warn "build $build"
               def statuses = build.releaseStatuses
               log.warn "current statuses $statuses"
               log.warn "Ci user ${(params['ciUser'])[0]}"
               statuses << new ReleaseStatus((params['status'])[0], (params['comment'])[0], (params['targetRepository'])[0], (params['ciUser'])[0], security.currentUsername)
               log.warn "New statues ${statuses[0]}"
               builds.saveBuild(build)
               message = " Build $buildName/$buildNumber has been successfully promoted"
               status = 200           
               }
        }
  }
}
