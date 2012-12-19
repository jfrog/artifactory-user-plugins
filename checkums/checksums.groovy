import org.apache.commons.codec.digest.DigestUtils
import org.artifactory.repo.RepoPath
import org.artifactory.request.Request

import static org.apache.commons.io.FilenameUtils.*
import static org.artifactory.repo.RepoPathFactory.create

download {

    //Algorithm names as defined in http://docs.oracle.com/javase/6/docs/technotes/guides/security/StandardNames.html
    //md5 and sha1 are omitted since they are supported by Artifactory natively
    def knownAlogrithms = ['sha256', 'sha384', 'sha512']

    /**
     * In case of resolution error provide an alternative response, by setting a success/error status code value and an optional error message.
     * Will not be called if the response is already committed (e.g. a previous error occurred).
     * As opposite to altResponse, called only for GET requests during which error occurred (e.g. 404 - not found, or 409 - conflict).
     *
     * Context variables:
     * status (int) - a response error status code (may be overridden in the plugin).
     * message (java.lang.String) - a response error message (may be overridden in the plugin).
     * inputStream (java.io.InputStream) - a new stream that provides the response content. Defaults to null.
     * size (long) - the size of the new content (helpful for clients processing the response). Defaults to -1.
     *
     * Closure parameters:
     * request (org.artifactory.request.Request) - a read-only parameter of the request.
     */
    afterDownloadError { Request request ->
        def requestedPath = request.repoPath.path
        if (isExtension(requestedPath, knownAlogrithms)) {
            RepoPath targetRepoPath = create(request.repoPath.repoKey, removeExtension(requestedPath))
            String extension = getExtension(requestedPath)
            def checksumPropertyName = "checksum.$extension"
            String savedChecksum = repositories.getProperty(targetRepoPath, checksumPropertyName)
            if (savedChecksum) {
                status = 200
                inputStream = new ByteArrayInputStream(savedChecksum.bytes)
            } else {
                def content = repositories.getContent(targetRepoPath)
                if (content.size >= 0) {
                    String checksum = content.inputStream.withStream { DigestUtils."${extension}Hex"(it) }
                    repositories.setProperty(targetRepoPath, checksumPropertyName, checksum)
                    status = 200
                    inputStream = new ByteArrayInputStream(checksum.bytes)
                }
            }
        }
    }
}