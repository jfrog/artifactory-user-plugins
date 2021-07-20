// Written by Shikhar Rawat, shikharr@jfrog.com
// Jfrog Inc.


import spock.lang.Specification
import java.security.MessageDigest
import org.jfrog.artifactory.client.model.repository.settings.impl.MavenRepositorySettingsImpl
import groovyx.net.http.HttpResponseException
import org.jfrog.artifactory.client.ArtifactoryClientBuilder


class DownloadDirectoryContentTest extends Specification {
    def 'download directory contest test'() {

        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = ArtifactoryClientBuilder.create().setUrl(baseurl)
            .setUsername('admin').setPassword('password').build()
        def auth = "Basic ${'admin:password'.bytes.encodeBase64()}"
        def xmlfile = new File('./src/test/groovy/DownloadDirectoryContentTest/maven-metadata.xml')
        def jarfile = new File('./src/test/groovy/DownloadDirectoryContentTest/lib-aopalliance-1.0.jar')
        def builder = artifactory.repositories().builders()
        def local = builder.localRepositoryBuilder().key('my-test-download')
        artifactory.repositories().create(0, local.build())

        //upload first file
        artifactory.repository("my-test-download")
                .upload("test/maven-metadata.xml", xmlfile)
                .withProperty("prop", "test")
                .doUpload()
        //upload second file
        artifactory.repository("my-test-download")
                .upload("test/lib-aopalliance-1.0.jar", jarfile)
                .withProperty("prop", "test")
                .doUpload()
        //sleep(3000)
        when:
        def proc = "curl -X GET -uadmin:password \"http://localhost:8088/artifactory/my-test-download/test;downloadDirectory+=true\" > ~/result.zip"
        def process = new ProcessBuilder([ "sh", "-c", proc])
                .directory(new File("/tmp"))
                .redirectErrorStream(true)
                .start()
        process.outputStream.close()
        process.inputStream.eachLine {println it}
        process.waitFor();
        //repositoryRequest(request)
        //sleep(3000)
        File zipfile = new File("~/result.zip")
        //sleep(2000)

        then:
        //def some = "false"

        println "The file has ${zipfile.length()} bytes"
        notThrown(HttpResponseException)


        cleanup:
        artifactory.repository("my-test-download").delete();
        println "deleted test repo from artifactory"
        //artifactory.repository("libs-release-local").delete("test/lib-aopalliance-1.0.jar")
        //artifactory.repository("libs-release-local").delete("test/maven-metadata.xml")

    }
}
