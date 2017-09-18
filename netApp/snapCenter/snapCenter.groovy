import groovy.json.JsonSlurper
@Grapes(
        @Grab(group = 'org.codehaus.groovy.modules.http-builder', module = 'http-builder', version = '0.7.1')
)
import groovyx.net.http.HTTPBuilder
import org.artifactory.resource.ResourceStreamHandle

import static groovyx.net.http.ContentType.JSON
import static groovyx.net.http.Method.POST
/*
curl -X POST --header 'Content-Type: application/json' --header 'Accept: application/json' --header 'Token: Token' -d '{
"Policy": {
    "Name": "Database_Daily"
}
}' 'https://sc1.netapp.local:8146/api/2.0/resourcegroups/db1_netapp_local_MySQL_artdb/backup'
*/

executions {
    snapCenter(httpMethod: 'POST', users: 'admins', params: [url: '', token: '', policy: '', resourcegroup: '']) { params, ResourceStreamHandle inputBody ->
        bodyJson = new JsonSlurper().parse(inputBody.inputStream)
        url = bodyJson.url
        token = bodyJson.token
        policy = bodyJson.policy
        resourcegroups = bodyJson.resourcegroups

        def http = new HTTPBuilder(url + '/api/2.0/resourcegroups/' + resourcegroups + '/backup')
        http.setHeaders(["Token": "$token"])
        http.ignoreSSLIssues( )
        http.request(POST, JSON) { req ->
            requestContentType = JSON
            body = [
                   Policy : [
                           "Name" : "$policy".toString()
                   ]
            ]
            response.success = { resp, json ->
                message = "Here is response from SnapCenter:$json"
                log.info message
            }

            response.failure = { resp, json ->
                message = "Error!! Here is response from SnapCenter: $json"
                log.info message
            }
        }
    }
}

