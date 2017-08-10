import org.jfrog.artifactory.client.model.repository.settings.impl.GenericRepositorySettingsImpl
import spock.lang.Shared
import spock.lang.Specification

import static org.jfrog.artifactory.client.ArtifactoryClient.create

class CranTest extends Specification {

    static final baseurl = 'http://localhost:8088/artifactory'
    static final repoKey = 'generic-local'
    static final adminPassword = 'admin:password'
    @Shared artifactory = create(baseurl, 'admin', 'password')

    def 'simple my plugin test'() {
        setup:
        def repo = createLocalCranRepo(repoKey)
//        def sourcePackage = 'fortunes_1.5-4.tar.gz'
//        def macBinPackage = 'cats_1.0.1.tgz'
//        def winBinPackage = 'Disake_1.5.zip'
//        def package2 = 'cats_1.0.0.tgz'
//        def package3 = 'AtmRay_1.31.tgz'
//        def targetPath = 'destination/cats_1.0.1.tgz'
//        repo.upload(sourcePackage, new File("./src/test/groovy/CranTest/$sourcePackage")).doUpload()
//        repo.upload(macBinPackage, new File("./src/test/groovy/CranTest/$macBinPackage")).doUpload()
//        repo.upload(winBinPackage, new File("./src/test/groovy/CranTest/$winBinPackage")).doUpload()
//        repo.upload(package2, new File('./src/test/groovy/CranTest/cats_1.0.0.tgz')).doUpload()
//        repo.upload(package3, new File('./src/test/groovy/CranTest/AtmRay_1.31.tgz')).doUpload()

        when:

        def testDir = new File('./src/test/groovy/CranTest')
        for (file in testDir.listFiles()) {
            if (file.name.endsWith('.zip') || file.name.endsWith('.tar.gz') || file.name.endsWith('.tgz')) {
                repo.upload(file.name, file).doUpload()
            }
        }


//        moveArtifact("$repoKey/$originPath", "$repoKey/$targetPath")
//        sleep(1000)
//        repo.upload(targetPath, new ByteArrayInputStream('test'.bytes)).doUpload()

        // def json = new JsonSlurper().parseText(artifactory.plugins().execute('myPlugin').aSync())

//        for (i in 1..2) {
//        artifactory.plugins().execute('cranIndex').withParameter('repoPath', 'repo/path/to/folder').aSync()
//            sleep(1000)
//        }
//        sleep(1000)
//        artifactory.plugins().execute('myPlugin').aSync()



        then:
        1 == 1

//        cleanup:
//        sleep(5000)
//        artifactory.repository(repoKey)?.delete()
    }

    def createLocalCranRepo(String repoKey) {
        def builder = artifactory.repositories().builders()
        def local = builder.localRepositoryBuilder().key(repoKey)
                .repositorySettings(new GenericRepositorySettingsImpl()).build()
        artifactory.repositories().create(0, local)

        "curl -X PUT -u$adminPassword $baseurl/api/storage/$repoKey?properties=cran=true".execute().waitFor()

        return artifactory.repository(repoKey)
    }

    def moveArtifact(originRepoPath, targetRepoPath) {
        "curl -X POST -u$adminPassword $baseurl/api/move/$originRepoPath?to=$targetRepoPath".execute().waitFor()
    }
}