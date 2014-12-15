import org.artifactory.repo.RepoPath
import org.artifactory.request.Request

/**
 * Created by Michal on 9/26/2014.
 */

download {
    beforeRemoteDownload {  Request request, RepoPath path ->
        if(request.getHeader()==""){

        }
    }
}