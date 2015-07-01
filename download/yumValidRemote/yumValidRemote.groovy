import org.artifactory.repo.RepoPathFactory
import java.security.MessageDigest

download {
    afterRemoteDownload { request, repoPath ->
        if (repoPath.path.endsWith('repodata/repomd.xml')) {
            def path = repoPath.path - ~'repodata/repomd\\.xml$'
            new XmlSlurper().parse(repositories.getContent(repoPath).getInputStream()).data.each {
                String file=it.location.@href
                log.debug "file is: "+file
                def rpath = RepoPathFactory.create(repoPath.repoKey, file)
                log.debug "rpath is: "+rpath
                def sumType = "${it.checksum[0].@type}"
                log.debug "sumType is:" +sumType
                def givenSum = "${it.checksum.text()}"
                log.debug "givenSum is: "+givenSum
                if (repositories.exists(rpath)) {
                    def calcSum
                    switch (sumType) {
                        case 'md5':
                            calcSum = repositories.getFileInfo(rpath).checksumsInfo.md5
                            break
                        case 'sha':
                        case 'sha1':
                            calcSum = repositories.getFileInfo(rpath).checksumsInfo.sha1
                            break
                        default:
                            def digest = MessageDigest.getInstance("SHA-${sumType - ~'^sha'}")
                            digest.update(repositories.getContent(rpath).inputStream.bytes)
                            calcSum = new BigInteger(1, digest.digest()).toString(16).padLeft(40, '0')
                    }
                    if (givenSum != calcSum) {
                        log.debug "Deleting file: "+rpath+" as it's checksum does not match to entry inside the repomd.xml "
                        repositories.delete(rpath)
                    }
                }
            }
        }
    }
}
