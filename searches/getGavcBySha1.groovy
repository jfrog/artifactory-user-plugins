import org.artifactory.repo.RepoPath

executions {
    /**
     * Returns GAVC by SHA1.
     * Usage: curl -u admin:password http://localhost:8081/artifactory/api/plugins/execute/getGavcBySha1?params=sha1=cf2171cba8bbbdf7f423f9ef54d8626e4011fd96
     */
    getGavcBySha1(version: '1.0', description: 'Returns GAVC by SHA1', httpMethod: 'GET') { params ->
        String sha1 = params['sha1'][0] //TODO check for parameter existence
        RepoPath artifact = searches.artifactsBySha1(sha1)?.first() //TODO check for more than one result
        message = repositories.getLayoutInfo(artifact) //TODO handle artifacts which don't match the repo layout
        status = 200
    }

}

