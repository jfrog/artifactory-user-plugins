import org.apache.http.client.HttpResponseException
import org.artifactory.build.Builds
import org.artifactory.exception.CancelException
import org.artifactory.repo.Repositories
import org.artifactory.search.Searches
import org.artifactory.security.Security
import org.jfrog.artifactory.client.Artifactory
import org.jfrog.artifactory.client.ArtifactoryClientBuilder
import org.jfrog.artifactory.client.ArtifactoryResponse
import org.jfrog.artifactory.client.ItemHandle
import org.jfrog.artifactory.client.impl.ArtifactoryRequestImpl
import org.jfrog.artifactory.client.model.File
import org.jfrog.artifactory.client.model.PluginType
import org.jfrog.artifactory.client.model.repository.settings.impl.MavenRepositorySettingsImpl
import org.slf4j.Logger
import spock.lang.Requires
import spock.lang.Specification
import spock.lang.Unroll

import java.nio.file.Files
import java.nio.file.Paths

import static org.jfrog.artifactory.client.ArtifactoryRequest.Method.POST

/**
 * This test expects the Artifactory docker container to run,
 * which can be started with ./startArtifactory.sh which is located next to this test file.
 */
class ArchiveOldArtifactsTest extends Specification {
    private static final Long MB5 = 1_000_000
    private static final Long MB100 = MB5 * 20
    private static final Long MB1000 = MB100 * 10
    private baseurl = 'http://localhost:8088/artifactory'
    private Artifactory artifactory
    private final static String defaultUploadRepoName = 'maven-local'
    private final static String defaultArchiveRepoName = 'maven-archive'

    void setup() {
        artifactory = ArtifactoryClientBuilder.create()
                .setUrl(baseurl)
                .setUsername('admin')
                .setPassword('password')
                .build()
    }

    def "Plugin should be loaded"() {
        when:
        def list = artifactory.plugins().list(PluginType.executions)

        then:
        list.find { it.name == 'archive_old_artifacts' }
    }

    @Unroll
    def 'Plugin doesn\'t run if #description'() {
        setup:
        createRepo(uploadRepoName)
        createRepo(archiveRepoName)
        uploadFile(uploadRepoName, filePath, stream)
        stream.reset() // reuse stream by resetting to the beginning

        when: 'plugin is executed'
        def response = executePluginWith(params)

        then: 'original still file exists'
        response.isSuccessResponse() == pluginExecutionsShouldSucceed
        def origFileContent = downloadFile(uploadRepoName, filePath).text
        origFileContent == stream.text

        when: 'downloading the file from the archive'
        downloadFile(archiveRepoName, filePath).text

        then: 'there is no file in the archive'
        def cause = thrown(HttpResponseException)
        cause.statusCode == 404

        cleanup:
        removeRepo(uploadRepoName)
        removeRepo(archiveRepoName)

        where:
        description                                                                     | params | filePath                              | uploadRepoName        | archiveRepoName        | stream           || pluginExecutionsShouldSucceed
        'no params are provided'                                                        | [
                :
        ]                                                                                        | 'foo.txt'                             | defaultUploadRepoName | defaultArchiveRepoName | someFileStream() || false
        'no archive criteria are provided'                                              | [
                srcRepo    : defaultUploadRepoName,
                archiveRepo: defaultArchiveRepoName,
        ]                                                                                        | 'foo.txt'                             | defaultUploadRepoName | defaultArchiveRepoName | someFileStream() || false
        'default params are provided'                                                   | [
                lastModifiedDays  : 0,
                lastUpdatedDays   : 0,
                createdDays       : 0,
                lastDownloadedDays: 0,
                ageDays           : 0,
                excludePropertySet: '',
                includePropertySet: '',
                srcRepo           : defaultUploadRepoName,
                archiveRepo       : defaultArchiveRepoName,
        ]                                                                                        | 'foo.txt'                             | defaultUploadRepoName | defaultArchiveRepoName | someFileStream() || false
        "filePattern doesn't match the filename, directory path not taken into account" | [
                filePattern       : 'com/company/product/*',
                lastModifiedDays  : 0,
                lastUpdatedDays   : 0,
                createdDays       : 0,
                lastDownloadedDays: 0,
                ageDays           : 0,
                excludePropertySet: 'nonexistingproperty',
                includePropertySet: '',
                srcRepo           : defaultUploadRepoName,
                archiveRepo       : defaultArchiveRepoName,
        ]                                                                                        | 'com/company/product/version/foo.txt' | defaultUploadRepoName | defaultArchiveRepoName | someFileStream() || true
        "filePattern doesn't match any artifacts"                                       | [
                pathPattern       : 'com/company/product/*',
                filePattern       : 'foo.txt',
                lastModifiedDays  : 0,
                lastUpdatedDays   : 0,
                createdDays       : 0,
                lastDownloadedDays: 0,
                ageDays           : 0,
                excludePropertySet: 'nonexistingproperty',
                includePropertySet: '',
                srcRepo           : defaultUploadRepoName,
                archiveRepo       : defaultArchiveRepoName,
        ]                                                                                        | 'com/company/product/version/foo.txt' | defaultUploadRepoName | defaultArchiveRepoName | someFileStream() || true
    }

    @Unroll
    def 'Artifact [#filePath] can be archived based on path [#pathPattern] and file [#filePattern] patterns'() {
        setup:
        createRepo(uploadRepoName)
        createRepo(archiveRepoName)
        def archivePropertyName = 'archive'

        when: 'artifact exists'
        uploadFile(uploadRepoName, filePath, stream)
        setPropertyOnFile(uploadRepoName, filePath, archivePropertyName, 'yes')

        def params = [
                pathPattern       : pathPattern,
                filePattern       : filePattern,
                includePropertySet: archivePropertyName,
                srcRepo           : uploadRepoName,
                archiveRepo       : archiveRepoName,
        ]
        def response = executePluginWith(params)

        then: 'it gets archived to the archive repo, and replaced with an informative message of the archiving'
        response.isSuccessResponse()
        def file = downloadFileMetadata(archiveRepoName, filePath)
        file.getPropertyValues('archived.timestamp')
        def fileContent = downloadFile(archiveRepoName, filePath).text
        fileContent == '''\
            This artifact has been archived!
            '''.stripIndent()

        cleanup:
        removeRepo(uploadRepoName)
        removeRepo(archiveRepoName)

        where:
        pathPattern                | filePattern | filePath                              | uploadRepoName        | archiveRepoName        | stream
        '**'                       | '*'         | 'foo.txt'                             | defaultUploadRepoName | defaultArchiveRepoName | someFileStream()
        '*'                        | '*'         | 'foo.txt'                             | defaultUploadRepoName | defaultArchiveRepoName | someFileStream()
        '*'                        | '*.txt'     | 'foo.txt'                             | defaultUploadRepoName | defaultArchiveRepoName | someFileStream()
        '*'                        | 'foo.txt'   | 'foo.txt'                             | defaultUploadRepoName | defaultArchiveRepoName | someFileStream()
        '**'                       | 'foo.txt'   | 'com/company/product/version/foo.txt' | defaultUploadRepoName | defaultArchiveRepoName | someFileStream()
        '**/*'                     | 'foo.txt'   | 'com/company/product/version/foo.txt' | defaultUploadRepoName | defaultArchiveRepoName | someFileStream()
        '**/*'                     | 'fo?.txt'   | 'com/company/product/version/foo.txt' | defaultUploadRepoName | defaultArchiveRepoName | someFileStream()
        '**/*'                     | 'f*.txt'    | 'com/company/product/version/foo.txt' | defaultUploadRepoName | defaultArchiveRepoName | someFileStream()
        '**/*'                     | '*.txt'     | 'com/company/product/version/foo.txt' | defaultUploadRepoName | defaultArchiveRepoName | someFileStream()
        'com/company/product/**/*' | '*.txt'     | 'com/company/product/version/foo.txt' | defaultUploadRepoName | defaultArchiveRepoName | someFileStream()
        'com/company/product/**'   | '*.txt'     | 'com/company/product/version/foo.txt' | defaultUploadRepoName | defaultArchiveRepoName | someFileStream()
    }

    @Unroll
    def 'Artifact [#filePath] not matching the path [#pathPattern] and file [#filePattern] patterns is not archived'() {
        setup:
        createRepo(uploadRepoName)
        createRepo(archiveRepoName)
        def archivePropertyName = 'archive'

        when: 'artifact exists'
        uploadFile(uploadRepoName, filePath, stream)
        stream.reset() // reuse stream by resetting to the beginning
        setPropertyOnFile(uploadRepoName, filePath, archivePropertyName, 'yes')

        def params = [
                pathPattern       : pathPattern,
                filePattern       : filePattern,
                includePropertySet: archivePropertyName,
                srcRepo           : uploadRepoName,
                archiveRepo       : archiveRepoName,
        ]
        def response = executePluginWith(params)

        then: 'original still file exists'
        response.isSuccessResponse()
        def origFileContent = downloadFile(uploadRepoName, filePath).text
        origFileContent == stream.text

        when: 'downloading the file from the archive'
        downloadFile(archiveRepoName, filePath).text

        then: 'there is no file in the archive'
        def cause = thrown(HttpResponseException)
        cause.statusCode == 404

        cleanup:
        removeRepo(uploadRepoName)
        removeRepo(archiveRepoName)

        where:
        pathPattern                         | filePattern | filePath                              | uploadRepoName        | archiveRepoName        | stream
        '**/*'                              | '*'         | 'foo.txt'                             | defaultUploadRepoName | defaultArchiveRepoName | someFileStream()
        '*'                                 | '*.tgz'     | 'foo.txt'                             | defaultUploadRepoName | defaultArchiveRepoName | someFileStream()
        '*'                                 | 'foo.txt'   | 'com/company/product/version/foo.txt' | defaultUploadRepoName | defaultArchiveRepoName | someFileStream()
        'org/**/*'                          | 'foo.txt'   | 'com/company/product/version/foo.txt' | defaultUploadRepoName | defaultArchiveRepoName | someFileStream()
        'com/company/someotherproduct/**/*' | '*.txt'     | 'com/company/product/version/foo.txt' | defaultUploadRepoName | defaultArchiveRepoName | someFileStream()
        'com/company/product/*'             | '*.txt'     | 'com/company/product/version/foo.txt' | defaultUploadRepoName | defaultArchiveRepoName | someFileStream()
    }

    @Unroll
    def 'Artifacts can be archived based on a property [#archivePropertyName]'() {
        setup:
        createRepo(uploadRepoName)
        createRepo(archiveRepoName)

        when: 'artifact with the "archive" property exists'
        uploadFile(uploadRepoName, filePath, stream)
        setPropertyOnFile(uploadRepoName, filePath, archivePropertyName, 'yes')

        def params = [
                includePropertySet: archivePropertyName,
                srcRepo           : uploadRepoName,
                archiveRepo       : archiveRepoName,
        ]
        def response = executePluginWith(params)

        then: 'it gets archived to the archive repo, and replaced with an informative message of the archiving'
        response.isSuccessResponse()
        def file = downloadFileMetadata(archiveRepoName, filePath)
        file.getPropertyValues('archived.timestamp')
        def fileContent = downloadFile(archiveRepoName, filePath).text
        fileContent == '''\
            This artifact has been archived!
            '''.stripIndent()

        cleanup:
        removeRepo(uploadRepoName)
        removeRepo(archiveRepoName)

        where:
        archivePropertyName | filePath  | uploadRepoName        | archiveRepoName        | stream
        'archive'           | 'foo.txt' | defaultUploadRepoName | defaultArchiveRepoName | someFileStream()
        'blaat'             | 'foo.txt' | defaultUploadRepoName | defaultArchiveRepoName | someFileStream()
    }

    @Unroll
    def 'Artifacts can be archived [#filePath from: #uploadRepoName to: #archiveRepoName]'() {
        setup:
        createRepo(uploadRepoName)
        createRepo(archiveRepoName)
        def archivePropertyName = 'archive'

        when: 'artifact with the "archive" property exists'
        uploadFile(uploadRepoName, filePath, stream)
        setPropertyOnFile(uploadRepoName, filePath, archivePropertyName, 'yes')

        def params = [
                includePropertySet: archivePropertyName,
                srcRepo           : uploadRepoName,
                archiveRepo       : archiveRepoName,
        ]
        def response = executePluginWith(params)

        then: 'it gets archived to the archive repo, and replaced with an informative message of the archiving'
        response.isSuccessResponse()
        def file = downloadFileMetadata(archiveRepoName, filePath)
        file.getPropertyValues('archived.timestamp')
        !file.getPropertyValues('archived.path')
        def fileContent = downloadFile(archiveRepoName, filePath).text
        fileContent == '''\
            This artifact has been archived!
            '''.stripIndent()

        cleanup:
        removeRepo(uploadRepoName)
        removeRepo(archiveRepoName)

        where:
        filePath                                  | uploadRepoName        | archiveRepoName        | stream
        'foo.txt'                                 | defaultUploadRepoName | defaultArchiveRepoName | someFileStream()
        'com/company/product/1.2.3/foo-1.2.3.jar' | defaultUploadRepoName | defaultArchiveRepoName | someFileStream()
    }

    @Unroll
    @Requires({ env['ARTIFACTORY_DATA_ARCHIVE'] })
    def 'Artifacts can also be archived to disk [#filePath]'() {
        setup:
        createRepo(uploadRepoName)
        createRepo(archiveRepoName)
        def archivePropertyName = 'archive'
        def artifactoryDataArchive = System.getenv('ARTIFACTORY_DATA_ARCHIVE')

        when: 'artifact with the "archive" property exists'
        uploadFile(uploadRepoName, filePath, stream)
        setPropertyOnFile(uploadRepoName, filePath, archivePropertyName, 'yes')

        def params = [
                includePropertySet: archivePropertyName,
                srcRepo           : uploadRepoName,
                archiveRepo       : archiveRepoName,
                copyArtifactToDisk: 'true',
        ]
        def response = executePluginWith(params)

        then: 'it gets archived to the archive repo, and replaced with an informative message of the archiving'
        response.isSuccessResponse()
        def file = downloadFileMetadata(archiveRepoName, filePath)
        file.getPropertyValues('archived.timestamp')
        file.getPropertyValues('archived.path')
        String archivedPath = file.getPropertyValues('archived.path').first()
        def fileContent = downloadFile(archiveRepoName, filePath).text
        fileContent == """\
            This artifact has been archived!
            Contact the Artifactory administrators if you really need this artifact.
            
            The artifact is archived to: ${archivedPath}
            """.stripIndent()

        String localArchivedPath = archivedPath.replace('/var/opt/jfrog/archive/', artifactoryDataArchive)
        artifactExistsInArchiveOnDisk(localArchivedPath)

        cleanup:
        removeRepo(uploadRepoName)
        removeRepo(archiveRepoName)
        removeArtifactFromArchiveOnDisk("${artifactoryDataArchive}/${defaultUploadRepoName}")

        where:
        filePath                                  | uploadRepoName        | archiveRepoName        | stream
        'foo.txt'                                 | defaultUploadRepoName | defaultArchiveRepoName | someFileStream()
        'com/company/product/1.2.3/foo-1.2.3.jar' | defaultUploadRepoName | defaultArchiveRepoName | someFileStream()
    }

    @Unroll
    @Requires({ env['ARTIFACTORY_DATA_ARCHIVE'] })
    def 'Leave [#numKeepArtifacts] artifacts in a flat directory structure'() {
        setup:
        def uploadRepoName = defaultUploadRepoName
        def archiveRepoName = defaultArchiveRepoName
        def archivePropertyName = 'archive'
        def filePath = '/com/company/product'
        def stream = someFileStream()
        createRepo(uploadRepoName)
        createRepo(archiveRepoName)
        def artifactoryDataArchive = System.getenv('ARTIFACTORY_DATA_ARCHIVE')
        def amountOfArtifacts = 12

        when: 'artifacts exist'
        def pathToVersionsFile = '/../src/test/resources/ArchiveOldArtifactsTest/versions.txt'
        def absolutePathToVersionsFile = Paths.get(artifactoryDataArchive, pathToVersionsFile).toFile().canonicalPath
        List<String> randomVersionsSubset = shuf(absolutePathToVersionsFile, amountOfArtifacts)
        def NUM_OF_FILES_UPLOADED_PER_VERSION = 2
        randomVersionsSubset.each { version ->
            stream.reset()
            uploadFile(uploadRepoName, "${generateArtifactPath(filePath, version)}.jar", stream)
            setPropertyOnFile(uploadRepoName, "${generateArtifactPath(filePath, version)}.jar", archivePropertyName, 'yes')

            stream.reset()
            uploadFile(uploadRepoName, "${generateArtifactPath(filePath, version)}.txt", stream)
            setPropertyOnFile(uploadRepoName, "${generateArtifactPath(filePath, version)}.txt", archivePropertyName, 'yes')
        }

        def params = [
                numKeepArtifacts  : numKeepArtifacts,
                includePropertySet: archivePropertyName, // selection criteria
                srcRepo           : uploadRepoName,
                archiveRepo       : archiveRepoName,
                copyArtifactToDisk: 'true',
        ]
        def response = executePluginWith(params)

        then: 'the expected amount of artifacts are archived to disk'
        response.isSuccessResponse()
        String localArchivedPath = Paths.get(artifactoryDataArchive, uploadRepoName, filePath)
        Files.list(Paths.get(localArchivedPath)).count() == ((randomVersionsSubset.size() * NUM_OF_FILES_UPLOADED_PER_VERSION) - numKeepArtifacts)

        cleanup:
        removeRepo(uploadRepoName)
        removeRepo(archiveRepoName)
        removeArtifactFromArchiveOnDisk("${artifactoryDataArchive}/${defaultUploadRepoName}")

        where:
        numKeepArtifacts || _
        1                || _
        10               || _
    }

    /**
     * This test is slow, and heavy on disk-space!
     */
    @Unroll
    @Requires({ env['ARTIFACTORY_DATA_ARCHIVE'] })
    def 'Works with [#amountOfArtifacts] artifacts of [#humanReadableSize] each'() {
        setup:
        def uploadRepoName = defaultUploadRepoName
        def archiveRepoName = defaultArchiveRepoName
        def archivePropertyName = 'archive'
        def filePath = '/com/company/product'
        def stream = someFileStream()
        def bigFileStream = repeat(content(), artifactSize)
        createRepo(uploadRepoName)
        createRepo(archiveRepoName)
        def artifactoryDataArchive = System.getenv('ARTIFACTORY_DATA_ARCHIVE')

        when: 'artifacts exist'
        def pathToVersionsFile = '/../src/test/resources/ArchiveOldArtifactsTest/versions.txt'
        def absolutePathToVersionsFile = Paths.get(artifactoryDataArchive, pathToVersionsFile).toFile().canonicalPath
        List<String> randomVersionsSubset = shuf(absolutePathToVersionsFile, amountOfArtifacts)
        def NUM_OF_FILES_UPLOADED_PER_VERSION = 2
        randomVersionsSubset.each { version ->
            bigFileStream.reset()
            uploadFile(uploadRepoName, "${generateArtifactPath(filePath, version)}.jar", bigFileStream)
            setPropertyOnFile(uploadRepoName, "${generateArtifactPath(filePath, version)}.jar", archivePropertyName, 'yes')

            stream.reset()
            uploadFile(uploadRepoName, "${generateArtifactPath(filePath, version)}.txt", stream)
            setPropertyOnFile(uploadRepoName, "${generateArtifactPath(filePath, version)}.txt", archivePropertyName, 'yes')
        }

        def params = [
                numKeepArtifacts  : numKeepArtifacts,
                includePropertySet: archivePropertyName, // selection criteria
                srcRepo           : uploadRepoName,
                archiveRepo       : archiveRepoName,
                copyArtifactToDisk: 'true',
        ]
        def response = executePluginWith(params)

        then: 'the expected amount of artifacts are archived to disk'
        response.isSuccessResponse()
        String firstVersionFromTheList = randomVersionsSubset.first()
        String localArchivedPath = Paths.get(artifactoryDataArchive, uploadRepoName, filePath)
        artifactExistsInArchiveOnDisk(generateArtifactPath(localArchivedPath, firstVersionFromTheList) + '.jar')
        Files.list(Paths.get(localArchivedPath)).count() == ((randomVersionsSubset.size() * NUM_OF_FILES_UPLOADED_PER_VERSION) - numKeepArtifacts)

        cleanup:
        removeRepo(uploadRepoName)
        removeRepo(archiveRepoName)
        removeArtifactFromArchiveOnDisk("${artifactoryDataArchive}/${defaultUploadRepoName}")

        where:
        amountOfArtifacts | artifactSize | humanReadableSize                                                 || numKeepArtifacts
        10                | MB5          | mockPlugin().first().toHumanSize(content().size() * artifactSize) || 3
        5                 | MB100        | mockPlugin().first().toHumanSize(content().size() * artifactSize) || 2
        2                 | MB1000       | mockPlugin().first().toHumanSize(content().size() * artifactSize) || 1
    }

    @Unroll
    def "Convert [#bytes] bytes toHumanSize [#humanSize]"() {
        // Test mocked toHumanSize functionality, as there is no real connection to the plugin source from here
        MockArtifactoryPluginInsideArtifactoryContext plugin
        def __
        (plugin, __, __, __, __, __) = mockPlugin()
        expect:
        plugin.toHumanSize(bytes as long) == humanSize

        where:
        bytes                                  || humanSize
        0l                                     || '0 bytes'
        1024l                                  || '1 KB'
        1024l * 1024l                          || '1 MB'
        1024l * 1024l * 1024l                  || '1 GB'
        (1024l * 1024l * 1024l) * 1.235        || '1.235 GB'
        1024l * 1024l * 1024l * 1024l          || '1 TB'
        1024l * 1024l * 1024l * 1024l * 1024l  || '1 PB'
        1024l * 1024l * 1024l * 1024l * 2l     || '2 TB'
        (1024l * 1024l * 1024l * 1024l) * 1.5  || '1.5 TB'
        (1024l * 1024l * 1024l * 1024l) * 1.45 || '1.45 TB'
    }

    @Unroll
    def "Convert string-based http param [#value] correctly to type [#type] with value [#expected]"() {
        // Test mocked ArchiveConfig functionality, as there is no real connection to the plugin source from here
        expect:
        if (type == Boolean && value instanceof String) {
            if (value.toLowerCase() == 'false') {
                value = false
            }
        }
        value.asType(type) == expected

        where:
        value   || type    | expected
        'true'  || Boolean | true
        'TrEe'  || Boolean | true
        'false' || Boolean | false
        'FaLsE' || Boolean | false
    }

    @Unroll
    def "Translate includePropertySet/excludePropertySet [#propertiesString] string to map #expected"() {
        // Test mocked translatePropertiesString functionality, as there is no real connection to the plugin source from here
        MockArtifactoryPluginInsideArtifactoryContext plugin
        def __
        (plugin, __, __, __, __, __) = mockPlugin()

        expect:
        plugin.translatePropertiesString(propertiesString) == expected

        where:
        propertiesString                       || expected
        'key'                                  || ['key': null]
        'key:'                                 || ['key': null]
        'key:value'                            || ['key': 'value']
        'key;value'                            || ['key': null, 'value': null]
        'key1:value1;key2:value2'              || ['key1': 'value1', 'key2': 'value2']
        'key1:value1;key2:value2;'             || ['key1': 'value1', 'key2': 'value2']
        'key1:value1;key2:value2;key3:value3'  || ['key1': 'value1', 'key2': 'value2', 'key3': 'value3']
        'key1:value1;key2:value2;key3:value3;' || ['key1': 'value1', 'key2': 'value2', 'key3': 'value3']
        'key1;key2:value2;key3:'               || ['key1': null, 'key2': 'value2', 'key3': null]
        'archived.timestamp;'                  || ['archived.timestamp': null]
    }

    private List mockPlugin() {
        def repositories = Mock(Repositories)
        def searches = Mock(Searches)
        def security = Mock(Security)
        def builds = Mock(Builds)
        def log = Mock(Logger)
        def plugin = new MockArtifactoryPluginInsideArtifactoryContext(repositories, log, security, searches, builds)
        return [plugin, repositories, searches, security, builds, log]
    }


    private byte[] content() {
        return 'hello'.getBytes('utf-8')
    }

    private List<String> shuf(String filePath, int headCount) {
        def file = new java.io.File(filePath)
        def lines = file.readLines()
        Collections.shuffle(lines)
        return lines.subList(0, headCount < lines.size() ? headCount : lines.size())
    }

    private String generateArtifactPath(groupPath, artifactId) {
        return "${groupPath}/${artifactId}"
    }

    private boolean artifactExistsInArchiveOnDisk(String filePath) {
        def path = Paths.get(filePath)
        return Files.exists(path) &&
                Files.isRegularFile(path)
    }

    private void removeArtifactFromArchiveOnDisk(String filePath) {
        new java.io.File(filePath).deleteDir()
    }

    private String removeRepo(String uploadRepoName) {
        return artifactory.repository(uploadRepoName).delete()
    }

    private InputStream downloadFile(String archiveRepoName, String filePath) {
        return artifactory.repository(archiveRepoName)
                .download(filePath)
                .doDownload()
    }

    private ItemHandle downloadFileMetadata(String archiveRepoName, String filePath) {
        return artifactory.repository(archiveRepoName).file(filePath)
    }

    private ArtifactoryResponse executePluginWith(Map<String, String> params) {
        def pluginpath = "api/plugins/execute/archive_old_artifacts"
        def pluginreq = new ArtifactoryRequestImpl().apiUrl(pluginpath)
        def urlParams = params.collect {
            k, v -> "${k}=${v}"
        }.join('|')
        pluginreq.setQueryParams([
                params: urlParams
        ])
        pluginreq.method(POST)
        return artifactory.restCall(pluginreq)
    }

    private InputStream someFileStream() {
        return new ByteArrayInputStream('some very important data'.getBytes('utf-8'))
    }

    private InputStream repeat(byte[] sample, long times) {
        return new InputStream() {
            private long pos = 0;
            private final long total = (long) sample.length * times;

            int read() throws IOException {
                return pos < total ?
                        sample[(int) (pos++ % sample.length)] :
                        -1;
            }

            @Override
            void reset() throws IOException {
                pos = 0
            }
        };
    }

    private void setPropertyOnFile(String repoName, String filePath, String propertyName, String propertyValue) {
        artifactory.repository(repoName)
                .file(filePath)
                .properties()
                .addProperty(propertyName, propertyValue)
                .doSet()
    }

    private File uploadFile(String repoName, String filePath, InputStream stream) {
        return artifactory.repository(repoName)
                .upload(filePath, stream)
                .doUpload()
    }

    private void createRepo(String repoName) {
        def builder = artifactory.repositories().builders()
        def local1 = builder.localRepositoryBuilder().key(repoName)
                .repositorySettings(new MavenRepositorySettingsImpl())
                .build()
        artifactory.repositories().create(0, local1)
    }


}

class MockArtifactoryPluginInsideArtifactoryContext {
    private Repositories repositories
    private Logger log
    private Security security
    private Searches searches
    private Builds builds

    MockArtifactoryPluginInsideArtifactoryContext(Repositories repositories, Logger log, Security security, Searches searches, Builds builds) {
        this.repositories = repositories
        this.log = log
        this.security = security
        this.searches = searches
        this.builds = builds
    }


    /**
     * Replica of the method in the actual plugin.
     * Replica for testing, as there's no connection between the test and the actual plugin.
     */
    String toHumanSize(long bytes) {
        long base = 1024L
        int decimals = 3
        def postfix = [' bytes', ' KB', ' MB', ' GB', ' TB', ' PB']
        int i = Math.log(bytes) / Math.log(base) as int
        i = (i >= postfix.size() ? postfix.size() - 1 : i)
        try {
            return Math.round((bytes / base**i) * 10**decimals) / 10**decimals + postfix[i]
        } catch (Exception cause) {
            return bytes + postfix[0]
        }
    }

    /**
     * Replica of the method in the actual plugin.
     * Replica for testing, as there's no connection between the test and the actual plugin.
     */
    Map<String, String> translatePropertiesString(String properties) {
        if (!(properties ==~ /(\w.+)(:\w.)*(;(\w.+)(:\w.)*)*/)) {
            throw new CancelException("Incorrect format of proarchiveOldArtifacts.groovyperties: ${properties}. Exiting now!", 400)
        }
        Map<String, String> result = new HashMap()
        String[] propertySets = properties.tokenize(';')
        propertySets.each {
            def (key, value) = it.tokenize(':')
            result.put(key, value)
        }
        return result
    }

}
