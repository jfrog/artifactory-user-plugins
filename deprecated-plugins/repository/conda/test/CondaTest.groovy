import spock.lang.Specification
import org.jfrog.artifactory.client.model.repository.settings.impl.GenericRepositorySettingsImpl
import groovy.json.JsonSlurper

import org.jfrog.artifactory.client.ArtifactoryClientBuilder

class CondaTest extends Specification {
    def 'conda plugin test'() {
        setup:
        def baseurl = 'http://localhost:8088/artifactory'
        def artifactory = ArtifactoryClientBuilder.create().setUrl(baseurl)
            .setUsername('admin').setPassword('password').build()
        // create the conda testing repo
        def builder = artifactory.repositories().builders()
        def local = builder.localRepositoryBuilder().key('conda-local')
        .repositorySettings(new GenericRepositorySettingsImpl()).build()
        artifactory.repositories().create(0, local)
        def localrepo = artifactory.repository('conda-local')
        // add the conda property so the plugin knows to index it
        def conn = new URL("${baseurl}/api/storage/conda-local/?properties=conda=").openConnection()
        conn.requestMethod = 'PUT'
        conn.setRequestProperty('Authorization', "Basic ${'admin:password'.bytes.encodeBase64()}")
        assert conn.responseCode == 204
        conn.disconnect()

        when:
        // upload a conda package
        def pack = new File('./src/test/groovy/CondaTest/pymc-2.3.6-np110py35_p0.tar.bz2')
        localrepo.upload('pymc-2.3.6-np110py35_p0.tar.bz2', pack).doUpload()

        // wait for calculation to occur (the job runs every ten seconds, so wait eleven)
        sleep(11000)
        // download the conda package's metadata
        localrepo.file('linux-64/pymc-2.3.6-np110py35_p0.tar.bz2').info()
        def repodata = localrepo.download('linux-64/repodata.json').doDownload()
        def json = new JsonSlurper().parse(repodata)

        then:
        // check that the metadata is correct
        json.packages['pymc-2.3.6-np110py35_p0.tar.bz2'].license == "Academic Free License"

        when:
        // upload a conda package that does not have subdir metadata info
        pack = new File('./src/test/groovy/CondaTest/pymc-2.3.7-np110py35_p0.tar.bz2')
        localrepo.upload('pymc-2.3.7-np110py35_p0.tar.bz2', pack).doUpload()
        // wait for calculation to occur (the job runs every ten seconds, so wait eleven)
        sleep(11000)
        // download the conda package's metadata
        localrepo.file('pymc-2.3.7-np110py35_p0.tar.bz2').info()
        repodata = localrepo.download('repodata.json').doDownload()
        json = new JsonSlurper().parse(repodata)

        then:
        // check that the metadata is correct
        json.packages['pymc-2.3.7-np110py35_p0.tar.bz2'].license == "Academic Free License"

        cleanup:
        // delete the testing repo
        localrepo?.delete()
    }
}
