import org.artifactory.build.*
import org.artifactory.repo.*
import org.artifactory.exception.CancelException
import groovy.json.JsonSlurper
import org.artifactory.resource.ResourceStreamHandle
import org.artifactory.common.StatusHolder


import java.lang.reflect.Array

import static groovy.xml.XmlUtil.serialize
import static org.artifactory.repo.RepoPathFactory.create

executions {

    dockerRetag(httpMethod: 'POST', users: [], groups: []) {ResourceStreamHandle body ->
    	log.info "entering dockerRetag"
        bodyJson = new JsonSlurper().parse(body.inputStream)
	sourceTagPath = createTagPath(bodyJson.dockerImage, bodyJson.sourceTag)
	rpf = new RepoPathFactory()
        sourcePath = rpf.create(bodyJson.sourceRepo, sourceTagPath)
        if(!repositories.exists(sourcePath)) {
	    log.error("sourceTag does not exist!")
	    status = 422
	    return
	    }
	if(sourcePath.isFolder()) {
		log.info "dockerRetag: TagPath is Folder"
		}
	else {
		message = "TagPath: "+sourcePath.toPath()+"from "+sourceTagPath+" is invalid!"
		log.error message
		status = 422
		return
            }
	destTagPath = createTagPath(bodyJson.dockerImage, bodyJson.destTag)
	destPath = rpf.create(bodyJson.sourceRepo, destTagPath)
        if(repositories.exists(destPath)) {
	    message = "dockerRetag deleting existing tag: "+destPath.toPath()
	    log.warn message
	    repositories.deleteAtomic(destPath)
            }
	try {
	    repositories.copyAtomic(sourcePath,destPath)
	} catch (Exception e) {
	    log.error "failed to copy docker tag"
	    status = 500
	    return
	}
	newManifest = rpf.create(bodyJson.sourceRepo, tagToManifest(destTagPath))
        if(newManifest.isFile()) {
                log.info "dockerRetag: dest manifest is file"
                }
        else {
	     message	= "TagPath: "+newManifest.toPath()+" is invalid!"
                log.error message
                status = 422
                return
            }
	repositories.setProperty(newManifest, "docker.manifest", bodyJson.destTag)
	message = "dockerRetag Successfully created tag" + destPath.toPath()
	log.info message
	status = 200        
   }
}

private String createTagPath(String dockerImage, String dockerTag) {
    return dockerImage+"/"+dockerTag+"/"
}

private String tagToManifest(String dockerTag) {
    return dockerTag + "manifest.json"
}

