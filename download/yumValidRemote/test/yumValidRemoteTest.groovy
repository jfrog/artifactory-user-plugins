import org.jfrog.artifactory.client.model.builder.impl.RepositoryBuildersImpl
import spock.lang.Specification

import java.security.MessageDigest

import static org.jfrog.artifactory.client.ArtifactoryClient.create
import static org.jfrog.artifactory.client.model.impl.RemoteRepoChecksumPolicyTypeImpl.pass_thru

class YumValidRemoteTest extends Specification {
    def 'check valid yum remote test'() {
        setup:
        def builder = RepositoryBuildersImpl.create()
        // local yum repo
        def local = builder.localRepositoryBuilder().key('yum-local')
        local.yumRootDepth(0).calculateYumMetadata(false)
        // remote yum repo, points to local
        def remote = builder.remoteRepositoryBuilder().key('yum-remote')
        remote.url('http://localhost:8088/artifactory/yum-local')
        remote.remoteRepoChecksumPolicyType(pass_thru)
        remote.username('admin').password('password')
        // create the new repos
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = create(baseurl, 'admin', 'password')
        artifactory.repositories().create(0, local.build())
        artifactory.repositories().create(0, remote.build())
        def localrepo = artifactory.repository('yum-local')
        def remoterepo = artifactory.repository('yum-remote')
        def remoterepocache = artifactory.repository('yum-remote-cache')

        when:
        // upload some yum repo files
        def repomd = new File('./src/test/groovy/yumValidRemoteTest/test1/repomd.xml')
        def primary = new File('./src/test/groovy/yumValidRemoteTest/test1/primary.xml.gz')
        def flists = new File('./src/test/groovy/yumValidRemoteTest/test1/filelists.xml.gz')
        def other = new File('./src/test/groovy/yumValidRemoteTest/test1/other.xml.gz')
        localrepo.folder('repodata').create()
        localrepo.upload('repodata/repomd.xml', repomd).doUpload()
        localrepo.upload('repodata/primary.xml.gz', primary).doUpload()
        localrepo.upload('repodata/filelists.xml.gz', flists).doUpload()
        localrepo.upload('repodata/other.xml.gz', other).doUpload()
        // download the repomd file from the remote
        def filedata = []
        def mdstream = remoterepo.download('repodata/repomd.xml').doDownload()
        new XmlParser().parse(mdstream).each {
            if (it.name() != 'data' && it.name()?.getLocalPart() != 'data')
                return
            String path = it.location.@href[0]
            if (path == null) return
            // try downloading the file, get the real checksum
            def istream = remoterepo.download(path).doDownload()
            def digest = MessageDigest.getInstance('SHA1')
            def buf = new byte[4096]
            def len = istream.read(buf)
            while (len != -1) {
                digest.update(buf, 0, len)
                len = istream.read(buf)
            }
            String realchecksum = digest.digest().encodeHex().toString()
            // get the filename and the checksum from the repomd
            String checksum = it.checksum.text().trim()
            filedata.push([path, checksum, realchecksum])
        }

        then:
        // ensure that all the filenames contain their checksums
        filedata[0][0].contains(filedata[0][1]) && filedata[0][1] == filedata[0][2]
        filedata[1][0].contains(filedata[1][1]) && filedata[1][1] == filedata[1][2]
        filedata[2][0].contains(filedata[2][1]) && filedata[2][1] == filedata[2][2]

        when:
        localrepo.delete('repodata')
        remoterepocache.delete('repodata/repomd.xml')
        // upload some yum repo files
        repomd = new File('./src/test/groovy/yumValidRemoteTest/test2/repomd.xml')
        primary = new File('./src/test/groovy/yumValidRemoteTest/test2/primary.xml.gz')
        flists = new File('./src/test/groovy/yumValidRemoteTest/test2/filelists.xml.gz')
        other = new File('./src/test/groovy/yumValidRemoteTest/test2/other.xml.gz')
        localrepo.folder('repodata').create()
        localrepo.upload('repodata/repomd.xml', repomd).doUpload()
        localrepo.upload('repodata/primary.xml.gz', primary).doUpload()
        localrepo.upload('repodata/filelists.xml.gz', flists).doUpload()
        localrepo.upload('repodata/other.xml.gz', other).doUpload()
        // download the repomd file from the remote
        filedata = []
        mdstream = remoterepo.download('repodata/repomd.xml').doDownload()
        new XmlParser().parse(mdstream).each {
            if (it.name() != 'data' && it.name()?.getLocalPart() != 'data')
                return
            String path = it.location.@href[0]
            if (path == null) return
            // try downloading the file, get the real checksum
            def istream = remoterepo.download(path).doDownload()
            def digest = MessageDigest.getInstance('SHA1')
            def buf = new byte[4096]
            def len = istream.read(buf)
            while (len != -1) {
                digest.update(buf, 0, len)
                len = istream.read(buf)
            }
            String realchecksum = digest.digest().encodeHex().toString()
            // get the filename and the checksum from the repomd
            String checksum = it.checksum.text().trim()
            filedata.push([path, checksum, realchecksum])
        }

        then:
        // ensure that all the filenames contain their checksums
        filedata[0][0].contains(filedata[0][1]) && filedata[0][1] == filedata[0][2]
        filedata[1][0].contains(filedata[1][1]) && filedata[1][1] == filedata[1][2]
        filedata[2][0].contains(filedata[2][1]) && filedata[2][1] == filedata[2][2]

        cleanup:
        // delete the testing repos
        localrepo?.delete()
        remoterepo?.delete()
    }
}
