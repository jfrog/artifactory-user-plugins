import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import java.text.SimpleDateFormat
import java.text.ParseException

import org.artifactory.build.DetailedBuildRunImpl
import org.artifactory.request.RequestThreadLocal
import org.artifactory.util.HttpUtils

build {
    afterSave { buildRun ->
        def cfgfile = new File(ctx.artifactoryHome.etcDir, 'plugins/atlassian.json')
        def cfg = new JsonSlurper().parse(cfgfile)
        def arturl = ctx.centralConfig.descriptor.urlBase
        if (!arturl) {
            def req = RequestThreadLocal.context.get().requestThreadLocal
            arturl = HttpUtils.getServletContextUrl(req.request)
        }
        arturl = arturl - ~'/artifactory/?$'
        def build = (buildRun as DetailedBuildRunImpl).build
        if (!build.vcsUrl?.startsWith(cfg.bitbucketUrl)) {
            return
        }
        // Process the started date
        def fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
        def date
        try {
            date = fmt.parse(build.started)
        } catch (ParseException ex) {
            log.warn("Unable to parse build started timestamp $build.started: $ex")
            return
        }
        // Generate the build url
        def buildurl = arturl + "/ui/builds/$build.name/$build.number/$date.time"
        // Build the request body
        def body = [:]
        body.server = arturl + '/artifactory'
        body.state = 'SUCCESSFUL'
        body.key = build.name
        body.resultKey = build.name + ':' + build.number
        body.name = build.name
        body.url = buildurl
        body.description = "$build.name build $build.number was successfully published to Artifactory at $build.started by ${build.agent?.name}/${build.agent?.version} using tool ${build.buildAgent?.name}/${build.buildAgent?.version}"
        body.duration = build.durationMillis/1000
        def bodytext = new JsonBuilder(body).toPrettyString()
        log.debug("Build triggered, payload follows:\n$bodytext")
        def url = "$cfg.bitbucketUrl/rest/build-status/1.0/commits/$build.vcsRevision"
        def auth = "Basic ${"$cfg.username:$cfg.password".bytes.encodeBase64().toString()}"
        def conn = new URL(url).openConnection()
        conn.doOutput = true
        conn.requestMethod = 'POST'
        conn.setRequestProperty('Content-Type', 'application/json')
        conn.setRequestProperty('Authorization', auth)
        conn.outputStream << bodytext
        if (conn.responseCode == 204) {
            log.debug("Success!")
        } else {
            log.debug("Failure. $conn.responseCode")
        }
    }
}
