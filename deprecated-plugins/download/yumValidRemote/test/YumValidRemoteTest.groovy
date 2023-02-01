import spock.lang.Specification
import org.jfrog.artifactory.client.model.repository.settings.impl.YumRepositorySettingsImpl

import java.security.MessageDigest

import org.jfrog.artifactory.client.ArtifactoryClientBuilder
import static org.jfrog.artifactory.client.model.impl.RemoteRepoChecksumPolicyTypeImpl.pass_thru

class YumValidRemoteTest extends Specification {
    def 'check valid yum remote test'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = ArtifactoryClientBuilder.create().setUrl(baseurl)
            .setUsername('admin').setPassword('password').build()

        def builder = artifactory.repositories().builders()

        def local = builder.localRepositoryBuilder().key('yum-local')
        .repositorySettings(new YumRepositorySettingsImpl()).build()
        artifactory.repositories().create(0, local)

        def remote = builder.remoteRepositoryBuilder().key('yum-remote')
        remote.repositorySettings(new YumRepositorySettingsImpl())
        remote.url('http://localhost:8088/artifactory/yum-local')
        remote.username('admin').password('password')
        artifactory.repositories().create(0, remote.build())

        def auth = "Basic ${'admin:password'.bytes.encodeBase64()}"
        def conn = new URL("${baseurl}/api/repositories/yum-remote").openConnection()
        conn.requestMethod = 'POST'
        conn.doOutput = true
        conn.setRequestProperty('Authorization', auth)
        conn.setRequestProperty('Content-Type', 'application/json')
        def textFile = "{\"remoteRepoChecksumPolicyType\":\"pass-thru\"}"
        conn.outputStream.write(textFile.bytes)
        assert conn.responseCode == 200
        conn.disconnect()

        def localrepo = artifactory.repository('yum-local')
        def remoterepo = artifactory.repository('yum-remote')
        def remoterepocache = artifactory.repository('yum-remote-cache')

        when:
        // upload some yum repo files
        def repomd = new File('./src/test/groovy/YumValidRemoteTest/test1/repomd.xml')
        def primary = new File('./src/test/groovy/YumValidRemoteTest/test1/primary.xml.gz')
        def flists = new File('./src/test/groovy/YumValidRemoteTest/test1/filelists.xml.gz')
        def other = new File('./src/test/groovy/YumValidRemoteTest/test1/other.xml.gz')
        localrepo.folder('repodata').create()
        localrepo.upload('repodata/repomd.xml', repomd).doUpload()
        localrepo.upload('repodata/primary.xml.gz', primary).doUpload()
        localrepo.upload('repodata/filelists.xml.gz', flists).doUpload()
        localrepo.upload('repodata/other.xml.gz', other).doUpload()

        // Wait for indexing
        sleep(5000l)

        // download the repomd file from the remote
        def filedata = []
        def mdstream = remoterepo.download('repodata/repomd.xml').doDownload()
        new XmlParser().parse(mdstream).each {
            if (it.name() != 'data' && it.name()?.getLocalPart() != 'data')
                return
            String path = it.location.@href[0]
            if (path == null) return
            // try downloading the file, get the real checksum
            sleep(1000)
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
        repomd = new File('./src/test/groovy/YumValidRemoteTest/test2/repomd.xml')
        primary = new File('./src/test/groovy/YumValidRemoteTest/test2/primary.xml.gz')
        flists = new File('./src/test/groovy/YumValidRemoteTest/test2/filelists.xml.gz')
        other = new File('./src/test/groovy/YumValidRemoteTest/test2/other.xml.gz')
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
            sleep(1000)
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
