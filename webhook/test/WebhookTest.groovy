import spock.lang.Specification
import groovy.json.JsonSlurper
import org.jfrog.artifactory.client.model.repository.settings.impl.MavenRepositorySettingsImpl
import org.jfrog.artifactory.client.model.repository.settings.impl.DockerRepositorySettingsImpl
import org.jfrog.artifactory.client.ArtifactoryClientBuilder
import org.jfrog.lilypad.Control

/**
 * Tests for Webhook
 *
 *
 * How the tests work:
 * Generally the tests work by uploading a secondary plugin into Artifactory. This plugin adds REST API points for
 * receiving events and storing the data. In other words, this secondary plugin is the target of events triggered by
 * the webhook. A typical test will:
 *
 * 1. Clear the secondary plugins buffer
 * 2. Upload a new webhook configuration file (with the event(s) we are testing)
 * 3. Reload the config
 * 4. Trigger the event
 * 5.
 *
 */
class WebhookTest extends Specification {
    // Artifactory
    static def BASE_URL = 'http://localhost:8088/artifactory'
    static def BASE_PORT = 8088
    static def RELOAD_PLUGINS_URL = 'api/plugins/reload'
    static def BUILD_UPLOAD_URL = 'api/build'
    static def MAVEN_REPO1_NAME = "maven-local"
    static def MAVEN_REPO2_NAME = "maven-local2"
    static def DOCKER_REPO_NAME = "docker-local"

    // Webhook
    static def WEBHOOK_PATH = '/var/opt/jfrog/artifactory/etc/plugins/webhook.groovy'
    static def WEBHOOK_CONFIG_PATH = '/var/opt/jfrog/artifactory/etc/plugins/webhook.config.json'
    static def WEBHOOK_INFO_URL = 'api/plugins/execute/webhookInfo'
    static def WEBHOOK_PING_URL = 'api/plugins/execute/pingWebhook'
    static def WEBHOOK_RELOAD_URL = 'api/plugins/execute/webhookReload'
    // Loopback
    static def WEBHOOK_LOOPBACK_PATH = '/var/opt/jfrog/artifactory/etc/plugins/webhookLoopback.groovy'
    static def WEBHOOK_LOOPBACK_CLEAR_URL = 'api/plugins/execute/webhookLoopbackClear'
    static def WEBHOOK_LOOPBACK_POST_URL = 'api/plugins/execute/webhookLoopback'
    static def WEBHOOK_LOOPBACK_GET_URL = 'api/plugins/execute/webhookLoopbackDetails'
    static def WEBHOOK_LOOPBACK_CODE = '''
import org.artifactory.resource.ResourceStreamHandle

class Globals {
    static def replies = []
}

executions {
    webhookLoopback (httpMethod: 'POST', users:['anonymous', 'admin']) { params, ResourceStreamHandle body ->
        Globals.replies << body.inputStream.text
    }
    webhookLoopbackClear (httpMethod: 'POST', users:['anonymous', 'admin']) {
        Globals.replies = []
    }
    webhookLoopbackDetails (httpMethod: 'GET', users:['anonymous', 'admin']) {
        message = Globals.replies
    }
}
'''

    // HTTP
    static def SUCCESS_CODE = 200

    // Other
    static def DEFAULT_SLEEP = 2000 // 2 seconds

    static def artifactory = null

    def setupSpec() {
        // Create some repositories
        artifactory = ArtifactoryClientBuilder.create().setUrl(BASE_URL)
                .setUsername('admin').setPassword('password').build()
        def builder = artifactory.repositories().builders()
        def local = builder.localRepositoryBuilder().key(MAVEN_REPO1_NAME)
                .repositorySettings(new MavenRepositorySettingsImpl()).build()
        def local2 = builder.localRepositoryBuilder().key(MAVEN_REPO2_NAME)
                .repositorySettings(new MavenRepositorySettingsImpl()).build()
        def local3 = builder.localRepositoryBuilder().key(DOCKER_REPO_NAME)
                .repositorySettings(new DockerRepositorySettingsImpl()).build()
        artifactory.repositories().create(0, local)
        artifactory.repositories().create(1, local2)
        artifactory.repositories().create(2, local3)


        // Load the loopback plugin used for testing
        Control.setFileContent(BASE_PORT, WEBHOOK_LOOPBACK_PATH, WEBHOOK_LOOPBACK_CODE)
        callPost("${BASE_URL}/${RELOAD_PLUGINS_URL}", "")
    }

    def cleanupSpec() {
        // Delete repositories
        artifactory.repository(MAVEN_REPO1_NAME).delete()
        artifactory.repository(MAVEN_REPO2_NAME).delete()
        artifactory.repository(DOCKER_REPO_NAME).delete()

        // Delete loopback plugin
        Control.setFileContent(BASE_PORT, WEBHOOK_LOOPBACK_PATH, "")
        callPost("${BASE_URL}/${RELOAD_PLUGINS_URL}", "")
        Control.deleteFolder(BASE_PORT, WEBHOOK_LOOPBACK_PATH)
    }

    /**
     * Loads the provided configuration into the webhook
     * @param config The contents of what should go in webhook.config.json
     */
    def reloadAndVerifyConfig(config) {
        Control.setFileContent(BASE_PORT, WEBHOOK_CONFIG_PATH, config)
        // Force plugin reload to avoid double hook issue
        Control.setFileContent(BASE_PORT, WEBHOOK_PATH, Control.getFileContent(BASE_PORT, WEBHOOK_PATH))
        callPost("${BASE_URL}/${RELOAD_PLUGINS_URL}", "")
        return callPost("${BASE_URL}/${WEBHOOK_RELOAD_URL}", "")
    }


    def 'test basic'() {
        setup:
        Control.setFileContent(BASE_PORT, WEBHOOK_CONFIG_PATH, "{}")

        // webhookInfo execution point
        when:
        def results = getRequest("${BASE_URL}/${WEBHOOK_INFO_URL}")
        then:
        results.code == SUCCESS_CODE && results.response.contains("Supported Events")

        // webhookReload execution point
        when:
        results = callPost("${BASE_URL}/${WEBHOOK_RELOAD_URL}", "")
        then:
        results.code == SUCCESS_CODE && results.response.contains("Reloaded")

        cleanup:
        Control.deleteFolder(BASE_PORT, WEBHOOK_CONFIG_PATH)
    }

    def 'test storage'() {
        setup:
        clearLoopbackBuffer()

        // 1. Test without the filter
        when:
        def reload = reloadAndVerifyConfig('''{
          "webhooks": {
              "test_webhook": {
                  "url": "http://localhost:8081/artifactory/api/plugins/execute/webhookLoopback",
                  "events": [
                    "storage.afterCreate"
                  ]
              }
          }
        }
        ''')
        reload.code == SUCCESS_CODE && reload.response.contains("Reloaded")
        artifactory.repository(MAVEN_REPO1_NAME).upload("test/storage/file1.txt",
                new ByteArrayInputStream('test'.getBytes('utf-8'))).doUpload()
        sleep(DEFAULT_SLEEP)
        def json = getLoopbackBuffer()
        then:
        assert json instanceof java.util.List
        json.size() == 1
        json[0].artifactory.webhook.data.createdBy == "admin"
        json[0].artifactory.webhook.data.name == "file1.txt"
        json[0].artifactory.webhook.event == "storage.afterCreate"
        json[0].artifactory.webhook.data.repoKey == MAVEN_REPO1_NAME


        // 2. Test delete
        when:
        clearLoopbackBuffer()
        reload = reloadAndVerifyConfig('''{
          "webhooks": {
              "test_webhook": {
                  "url": "http://localhost:8081/artifactory/api/plugins/execute/webhookLoopback",
                  "events": [
                    "storage.afterDelete"
                  ]
              }
          }
        }
        ''')
        reload.code == SUCCESS_CODE && reload.response.contains("Reloaded")
        artifactory.repository(MAVEN_REPO1_NAME).delete("test/storage/file1.txt")
        sleep(DEFAULT_SLEEP)
        json = getLoopbackBuffer()
        then:
        assert json instanceof java.util.List
        json.size() == 1
        json[0].artifactory.webhook.data.createdBy == "admin"
        json[0].artifactory.webhook.data.name == "file1.txt"
        json[0].artifactory.webhook.event == "storage.afterDelete"
        json[0].artifactory.webhook.data.repoKey == MAVEN_REPO1_NAME

        // 3. After move
        when:
        clearLoopbackBuffer()
        reload = reloadAndVerifyConfig('''{
          "webhooks": {
              "test_webhook": {
                  "url": "http://localhost:8081/artifactory/api/plugins/execute/webhookLoopback",
                  "events": [
                    "storage.afterMove"
                  ]
              }
          }
        }
        ''')
        reload.code == SUCCESS_CODE && reload.response.contains("Reloaded")
        artifactory.repository(MAVEN_REPO1_NAME).upload("test/storage/file1.txt",
                new ByteArrayInputStream('test'.getBytes('utf-8'))).doUpload()
        artifactory.repository(MAVEN_REPO1_NAME).file("test/storage/file1.txt").
                move(MAVEN_REPO1_NAME, "test/storage/moved/file1.txt")
        sleep(DEFAULT_SLEEP)
        json = getLoopbackBuffer()
        then:
        assert json instanceof java.util.List
        json.size() == 1
        json[0].artifactory.webhook.data.item.createdBy == "admin"
        json[0].artifactory.webhook.data.item.name == "file1.txt"
        json[0].artifactory.webhook.event == "storage.afterMove"
        json[0].artifactory.webhook.data.item.repoKey == MAVEN_REPO1_NAME


        // 4. After copy
        when:
        clearLoopbackBuffer()
        reload = reloadAndVerifyConfig('''{
          "webhooks": {
              "test_webhook": {
                  "url": "http://localhost:8081/artifactory/api/plugins/execute/webhookLoopback",
                  "events": [
                    "storage.afterCopy"
                  ]
              }
          }
        }
        ''')
        reload.code == SUCCESS_CODE && reload.response.contains("Reloaded")
        artifactory.repository(MAVEN_REPO1_NAME).file("test/storage/moved/file1.txt").
                copy(MAVEN_REPO1_NAME, "test/storage/file1.txt")
        sleep(DEFAULT_SLEEP)
        json = getLoopbackBuffer()
        then:
        assert json instanceof java.util.List
        json.size() == 1
        json[0].artifactory.webhook.data.item.createdBy == "admin"
        json[0].artifactory.webhook.data.item.name == "file1.txt"
        json[0].artifactory.webhook.event == "storage.afterCopy"
        json[0].artifactory.webhook.data.item.repoKey == MAVEN_REPO1_NAME

        // 5. After property create
        when:
        clearLoopbackBuffer()
        reload = reloadAndVerifyConfig('''{
          "webhooks": {
              "test_webhook": {
                  "url": "http://localhost:8081/artifactory/api/plugins/execute/webhookLoopback",
                  "events": [
                    "storage.afterPropertyCreate"
                  ]
              }
          }
        }
        ''')
        reload.code == SUCCESS_CODE && reload.response.contains("Reloaded")
        artifactory.repository(MAVEN_REPO1_NAME).upload("test/storage/afterPropertyCreate/file1.txt",
                new ByteArrayInputStream('test'.getBytes('utf-8')))
                .withProperty("foo", "bar")
                .doUpload()
        sleep(DEFAULT_SLEEP)
        json = getLoopbackBuffer()
        then:
        assert json instanceof java.util.List
        json.size() == 1
        json[0].artifactory.webhook.event == "storage.afterPropertyCreate"

        // 6. After property delete
        when:
        clearLoopbackBuffer()
        reload = reloadAndVerifyConfig('''{
          "webhooks": {
              "test_webhook": {
                  "url": "http://localhost:8081/artifactory/api/plugins/execute/webhookLoopback",
                  "events": [
                    "storage.afterPropertyDelete"
                  ]
              }
          }
        }
        ''')
        reload.code == SUCCESS_CODE && reload.response.contains("Reloaded")
        artifactory.repository(MAVEN_REPO1_NAME).
                file("test/storage/afterPropertyCreate/file1.txt").
                deleteProperties("foo")
        sleep(DEFAULT_SLEEP)
        json = getLoopbackBuffer()
        then:
        assert json instanceof java.util.List
        json.size() == 1
        json[0].artifactory.webhook.event == "storage.afterPropertyDelete"

        cleanup:
        Control.deleteFolder(BASE_PORT, WEBHOOK_CONFIG_PATH)
    }

    def 'test build'() {
        setup:
        clearLoopbackBuffer()

        // 1. Test explicit default value
        when:
        def reload = reloadAndVerifyConfig('''{
          "webhooks": {
              "test_webhook": {
                  "url": "http://localhost:8081/artifactory/api/plugins/execute/webhookLoopback",
                  "events": [
                    "build.afterSave"
                  ]
              }
          }
        }
        ''')
        reload.code == SUCCESS_CODE && reload.response.contains("Reloaded")
        callPut("${BASE_URL}/${BUILD_UPLOAD_URL}",
                '{"version": "1.0","name": "myWebhookBuild","number": "1.0","started": "2014-03-31T16:24:42.116+0300"}',
        ["Content-Type": "application/json"])
        sleep(DEFAULT_SLEEP)
        def json = getLoopbackBuffer()
        then:
        assert json instanceof java.util.List
        json.size() == 1
        json[0].artifactory.webhook.data.name == "myWebhookBuild"
        json[0].artifactory.webhook.data.number == "1.0"


        cleanup:
        Control.deleteFolder(BASE_PORT, WEBHOOK_CONFIG_PATH)
    }

    def 'test execute'() {
        setup:
        clearLoopbackBuffer()

        // 1. Test the execute command
        when:
        def reload = reloadAndVerifyConfig('''{
          "webhooks": {
              "test_webhook": {
                  "url": "http://localhost:8081/artifactory/api/plugins/execute/webhookLoopback",
                  "events": [
                    "execute.pingWebhook"
                  ]
              }
          }
        }
        ''')
        reload.code == SUCCESS_CODE && reload.response.contains("Reloaded")
        getRequest("${BASE_URL}/${WEBHOOK_PING_URL}")
        sleep(DEFAULT_SLEEP)
        def json = getLoopbackBuffer()
        then:
        assert json instanceof java.util.List
        json.size() == 1
        json[0].artifactory.webhook.data.message == "It works!"


        cleanup:
        Control.deleteFolder(BASE_PORT, WEBHOOK_CONFIG_PATH)
    }

    def 'test docker'() {
        setup:
        clearLoopbackBuffer()
        def manifest = "busybox/latest/manifest.json"

        // 1. Test explicit default value
        when:
        def reload = reloadAndVerifyConfig('''{
          "webhooks": {
              "test_webhook": {
                  "url": "http://localhost:8081/artifactory/api/plugins/execute/webhookLoopback",
                  "events": [
                    "docker.tagCreated"
                  ]
              }
          }
        }
        ''')
        reload.code == SUCCESS_CODE && reload.response.contains("Reloaded")
        artifactory.repository(DOCKER_REPO_NAME).upload(manifest,
                new ByteArrayInputStream('{}'.getBytes('utf-8'))).doUpload()
        sleep(DEFAULT_SLEEP)
        def json = getLoopbackBuffer()
        then:
        assert json instanceof java.util.List
        json.size() == 1
        json[0].artifactory.webhook.data.docker.image == "busybox"
        json[0].artifactory.webhook.data.docker.tag == "latest"

        // 1. Test slack value
        when:
        clearLoopbackBuffer()
        reload = reloadAndVerifyConfig('''{
          "webhooks": {
              "test_webhook": {
                  "url": "http://localhost:8081/artifactory/api/plugins/execute/webhookLoopback",
                  "events": [
                    "docker.tagDeleted"
                  ]
              }
          }
        }
        ''')
        reload.code == SUCCESS_CODE && reload.response.contains("Reloaded")
        artifactory.repository(DOCKER_REPO_NAME).delete(manifest)
        sleep(DEFAULT_SLEEP)
        json = getLoopbackBuffer()
        then:
        assert json instanceof java.util.List
        json.size() == 1
        json[0].artifactory.webhook.data.docker.image == "busybox"
        json[0].artifactory.webhook.data.docker.tag == "latest"

        cleanup:
        Control.deleteFolder(BASE_PORT, WEBHOOK_CONFIG_PATH)
    }

    def 'test formatters'() {
        setup:
        clearLoopbackBuffer()

        // 1. Test explicit default value
        when:
        def reload = reloadAndVerifyConfig('''{
          "webhooks": {
              "test_webhook": {
                  "url": "http://localhost:8081/artifactory/api/plugins/execute/webhookLoopback",
                  "events": [
                    "execute.pingWebhook"
                  ],
                  "format": "default"
              }
          }
        }
        ''')
        reload.code == SUCCESS_CODE && reload.response.contains("Reloaded")
        getRequest("${BASE_URL}/${WEBHOOK_PING_URL}")
        sleep(DEFAULT_SLEEP)
        def json = getLoopbackBuffer()
        then:
        assert json instanceof java.util.List
        json.size() == 1
        json[0].artifactory.webhook.data.message == "It works!"

        // 1. Test slack value
        when:
        clearLoopbackBuffer()
        reload = reloadAndVerifyConfig('''{
          "webhooks": {
              "test_webhook": {
                  "url": "http://localhost:8081/artifactory/api/plugins/execute/webhookLoopback",
                  "events": [
                    "execute.pingWebhook"
                  ],
                  "format": "slack"
              }
          }
        }
        ''')
        reload.code == SUCCESS_CODE && reload.response.contains("Reloaded")
        getRequest("${BASE_URL}/${WEBHOOK_PING_URL}")
        sleep(DEFAULT_SLEEP)
        json = getLoopbackBuffer()
        then:
        assert json instanceof java.util.List
        json.size() == 1
        assert json[0].text.contains("Artifactory:")

        cleanup:
        Control.deleteFolder(BASE_PORT, WEBHOOK_CONFIG_PATH)
    }

    def 'test path and repo combo filter'() {
        setup:
        clearLoopbackBuffer()

        // 1. Fail to pass both filters
        when:
        def reload = reloadAndVerifyConfig('''{
          "webhooks": {
              "test_webhook": {
                  "url": "http://localhost:8081/artifactory/api/plugins/execute/webhookLoopback",
                  "events": [
                    "storage.afterCreate"
                  ],
                  "repositories": [
                      "maven-local2"
                  ],
                  "path": "combo/*"
              }
          }
        }
        ''')
        reload.code == SUCCESS_CODE && reload.response.contains("Reloaded")
        artifactory.repository(MAVEN_REPO1_NAME).upload("test/combo/path/file1.txt",
                new ByteArrayInputStream('test'.getBytes('utf-8'))).doUpload()
        sleep(DEFAULT_SLEEP)
        def json = getLoopbackBuffer()
        then:
        assert json instanceof java.util.List
        json.size() == 0

        // 2. Fail to pass repo filters
        when:
        clearLoopbackBuffer()
        reload = reloadAndVerifyConfig('''{
          "webhooks": {
              "test_webhook": {
                  "url": "http://localhost:8081/artifactory/api/plugins/execute/webhookLoopback",
                  "events": [
                    "storage.afterCreate"
                  ],
                  "repositories": [
                      "maven-local2"
                  ],
                  "path": "test/combo/*"
              }
          }
        }
        ''')
        reload.code == SUCCESS_CODE && reload.response.contains("Reloaded")
        artifactory.repository(MAVEN_REPO1_NAME).upload("test/combo/path/file2.txt",
                new ByteArrayInputStream('test'.getBytes('utf-8'))).doUpload()
        sleep(DEFAULT_SLEEP)
        json = getLoopbackBuffer()
        then:
        assert json instanceof java.util.List
        json.size() == 0


        // 3. Fail to pass path filters
        when:
        clearLoopbackBuffer()
        reload = reloadAndVerifyConfig('''{
          "webhooks": {
              "test_webhook": {
                  "url": "http://localhost:8081/artifactory/api/plugins/execute/webhookLoopback",
                  "events": [
                    "storage.afterCreate"
                  ],
                  "repositories": [
                      "maven-local"
                  ],
                  "path": "combo/*"
              }
          }
        }
        ''')
        reload.code == SUCCESS_CODE && reload.response.contains("Reloaded")
        artifactory.repository(MAVEN_REPO1_NAME).upload("test/combo/path/file3.txt",
                new ByteArrayInputStream('test'.getBytes('utf-8'))).doUpload()
        sleep(DEFAULT_SLEEP)
        json = getLoopbackBuffer()
        then:
        assert json instanceof java.util.List
        json.size() == 0

        // 4. Pass both filters
        when:
        clearLoopbackBuffer()
        reload = reloadAndVerifyConfig('''{
          "webhooks": {
              "test_webhook": {
                  "url": "http://localhost:8081/artifactory/api/plugins/execute/webhookLoopback",
                  "events": [
                    "storage.afterCreate"
                  ],
                  "repositories": [
                      "maven-local"
                  ],
                  "path": "test/combo/*"
              }
          }
        }
        ''')
        reload.code == SUCCESS_CODE && reload.response.contains("Reloaded")
        artifactory.repository(MAVEN_REPO1_NAME).upload("test/combo/path/file4.txt",
                new ByteArrayInputStream('test'.getBytes('utf-8'))).doUpload()
        sleep(DEFAULT_SLEEP)
        json = getLoopbackBuffer()
        then:
        assert json instanceof java.util.List
        json.size() == 1
        json[0].artifactory.webhook.data.createdBy == "admin"
        json[0].artifactory.webhook.data.name == "file4.txt"
        json[0].artifactory.webhook.event == "storage.afterCreate"
        json[0].artifactory.webhook.data.repoKey == MAVEN_REPO1_NAME

        cleanup:
        Control.deleteFolder(BASE_PORT, WEBHOOK_CONFIG_PATH)
    }

    def 'test path filter'() {
        setup:
        clearLoopbackBuffer()

        // 1. Test without the filter
        when:
        def reload = reloadAndVerifyConfig('''{
          "webhooks": {
              "test_webhook": {
                  "url": "http://localhost:8081/artifactory/api/plugins/execute/webhookLoopback",
                  "events": [
                    "storage.afterCreate"
                  ]
              }
          }
        }
        ''')
        reload.code == SUCCESS_CODE && reload.response.contains("Reloaded")
        artifactory.repository(MAVEN_REPO1_NAME).upload("test/path/file1.txt",
                new ByteArrayInputStream('test'.getBytes('utf-8'))).doUpload()
        sleep(DEFAULT_SLEEP)
        def json = getLoopbackBuffer()
        then:
        assert json instanceof java.util.List
        json.size() == 1
        json[0].artifactory.webhook.data.createdBy == "admin"
        json[0].artifactory.webhook.data.name == "file1.txt"
        json[0].artifactory.webhook.event == "storage.afterCreate"
        json[0].artifactory.webhook.data.repoKey == MAVEN_REPO1_NAME


        // 2. Test with the filter (expect a result)
        when:
        clearLoopbackBuffer()
        reload = reloadAndVerifyConfig('''{
          "webhooks": {
              "test_webhook": {
                  "url": "http://localhost:8081/artifactory/api/plugins/execute/webhookLoopback",
                  "events": [
                    "storage.afterCreate"
                  ],
                  "path": "test/path/*"
              }
          }
        }
        ''')
        reload.code == SUCCESS_CODE && reload.response.contains("Reloaded")
        artifactory.repository(MAVEN_REPO1_NAME).upload("test/path/file2.txt",
                new ByteArrayInputStream('test'.getBytes('utf-8'))).doUpload()
        sleep(DEFAULT_SLEEP)
        json = getLoopbackBuffer()
        then:
        assert json instanceof java.util.List
        json.size() == 1
        json[0].artifactory.webhook.data.createdBy == "admin"
        json[0].artifactory.webhook.data.name == "file2.txt"
        json[0].artifactory.webhook.event == "storage.afterCreate"
        json[0].artifactory.webhook.data.repoKey == MAVEN_REPO1_NAME

        // 3. Test with the filter (expect no result)
        when:
        clearLoopbackBuffer()
        reload = reloadAndVerifyConfig('''{
          "webhooks": {
              "test_webhook": {
                  "url": "http://localhost:8081/artifactory/api/plugins/execute/webhookLoopback",
                  "events": [
                    "storage.afterCreate"
                  ],
                  "path": "test/path/*"
              }
          }
        }
        ''')
        reload.code == SUCCESS_CODE && reload.response.contains("Reloaded")
        artifactory.repository(MAVEN_REPO1_NAME).upload("test/path2/file2.txt",
                new ByteArrayInputStream('test'.getBytes('utf-8'))).doUpload()
        sleep(DEFAULT_SLEEP)
        json = getLoopbackBuffer()
        then:
        assert json instanceof java.util.List
        json.size() == 0

        // 4. Test with a more complex filter and expect a result
        when:
        clearLoopbackBuffer()
        reload = reloadAndVerifyConfig('''{
          "webhooks": {
              "test_webhook": {
                  "url": "http://localhost:8081/artifactory/api/plugins/execute/webhookLoopback",
                  "events": [
                    "storage.afterCreate"
                  ],
                  "path": "test/path/*/foo/*/*.txt"
              }
          }
        }
        ''')
        reload.code == SUCCESS_CODE && reload.response.contains("Reloaded")
        artifactory.repository(MAVEN_REPO1_NAME).upload("test/path/paco/foo/bar/file3.txt",
                new ByteArrayInputStream('test'.getBytes('utf-8'))).doUpload()
        sleep(DEFAULT_SLEEP)
        json = getLoopbackBuffer()
        then:
        assert json instanceof java.util.List
        json.size() == 1

        // 5. Test with a leading '/'
        when:
        clearLoopbackBuffer()
        reload = reloadAndVerifyConfig('''{
          "webhooks": {
              "test_webhook": {
                  "url": "http://localhost:8081/artifactory/api/plugins/execute/webhookLoopback",
                  "events": [
                    "storage.afterCreate"
                  ],
                  "path": "/test/path/*/foo/*/*.txt"
              }
          }
        }
        ''')
        reload.code == SUCCESS_CODE && reload.response.contains("Reloaded")
        artifactory.repository(MAVEN_REPO1_NAME).upload("test/path/paco/foo/bar/file4.txt",
                new ByteArrayInputStream('test'.getBytes('utf-8'))).doUpload()
        sleep(DEFAULT_SLEEP)
        json = getLoopbackBuffer()
        then:
        assert json instanceof java.util.List
        json.size() == 1

        // 6. Test with regex type chars (which should be escaped by the webhook)
        when:
        clearLoopbackBuffer()
        reload = reloadAndVerifyConfig('''{
          "webhooks": {
              "test_webhook": {
                  "url": "http://localhost:8081/artifactory/api/plugins/execute/webhookLoopback",
                  "events": [
                    "storage.afterCreate"
                  ],
                  "path": "test/path/with--dash/with$/*"
              }
          }
        }
        ''')
        reload.code == SUCCESS_CODE && reload.response.contains("Reloaded")
        artifactory.repository(MAVEN_REPO1_NAME).upload("test/path/with--dash/with\$/file5.txt",
                new ByteArrayInputStream('test'.getBytes('utf-8'))).doUpload()
        sleep(DEFAULT_SLEEP)
        json = getLoopbackBuffer()
        then:
        assert json instanceof java.util.List
        json.size() == 1

        cleanup:
        Control.deleteFolder(BASE_PORT, WEBHOOK_CONFIG_PATH)
    }

    def 'test repo filter'() {
        setup:
        clearLoopbackBuffer()

        // 1. Test without the filter
        when:
        def reload = reloadAndVerifyConfig('''{
          "webhooks": {
              "test_webhook": {
                  "url": "http://localhost:8081/artifactory/api/plugins/execute/webhookLoopback",
                  "events": [
                    "storage.afterCreate"
                  ]
              }
          }
        }
        ''')
        reload.code == SUCCESS_CODE && reload.response.contains("Reloaded")
        artifactory.repository(MAVEN_REPO1_NAME).upload("foo/bar/file1.txt",
                new ByteArrayInputStream('test'.getBytes('utf-8'))).doUpload()
        sleep(DEFAULT_SLEEP)
        def json = getLoopbackBuffer()
        then:
        assert json instanceof java.util.List
        json.size() == 1
        json[0].artifactory.webhook.data.createdBy == "admin"
        json[0].artifactory.webhook.data.name == "file1.txt"
        json[0].artifactory.webhook.data.repoKey == MAVEN_REPO1_NAME


        // 2. Test that the filter excludes when it should
        when:
        clearLoopbackBuffer()
        reload = reloadAndVerifyConfig('''{
          "webhooks": {
              "test_webhook": {
                  "url": "http://localhost:8081/artifactory/api/plugins/execute/webhookLoopback",
                  "events": [
                    "storage.afterCreate"
                  ],
                  "repositories": [
                      "maven-local2"
                  ]
              }
          }
        }
        ''')
        reload.code == SUCCESS_CODE && reload.response.contains("Reloaded")
        artifactory.repository(MAVEN_REPO1_NAME).upload("foo/bar/file2.txt",
                new ByteArrayInputStream('test'.getBytes('utf-8'))).doUpload()
        sleep(DEFAULT_SLEEP)
        json = getLoopbackBuffer()
        then:
        assert json instanceof java.util.List
        json.size() == 0


        // 3. Test that the filter does not block what it should not
        when:
        clearLoopbackBuffer()
        reload = reloadAndVerifyConfig('''{
          "webhooks": {
              "test_webhook": {
                  "url": "http://localhost:8081/artifactory/api/plugins/execute/webhookLoopback",
                  "events": [
                    "storage.afterCreate"
                  ],
                  "repositories": [
                      "maven-local"
                  ]
              }
          }
        }
        ''')
        reload.code == SUCCESS_CODE && reload.response.contains("Reloaded")
        artifactory.repository(MAVEN_REPO1_NAME).upload("foo/bar/file3.txt",
                new ByteArrayInputStream('test'.getBytes('utf-8'))).doUpload()
        sleep(DEFAULT_SLEEP)
        json = getLoopbackBuffer()
        then:
        assert json instanceof java.util.List
        json.size() == 1
        json[0].artifactory.webhook.data.createdBy == "admin"
        json[0].artifactory.webhook.data.name == "file3.txt"
        json[0].artifactory.webhook.data.repoKey == MAVEN_REPO1_NAME



        // 4. Test that the filter works with multiple fake and one real
        when:
        clearLoopbackBuffer()
        reload = reloadAndVerifyConfig('''{
          "webhooks": {
              "test_webhook": {
                  "url": "http://localhost:8081/artifactory/api/plugins/execute/webhookLoopback",
                  "events": [
                    "storage.afterCreate"
                  ],
                  "repositories": [
                      "iDontExist", "meNeihter", "paco", "plantilla", "maven-local", "last"
                  ]
              }
          }
        }
        ''')
        reload.code == SUCCESS_CODE && reload.response.contains("Reloaded")
        artifactory.repository(MAVEN_REPO1_NAME).upload("foo/bar/file4.txt",
                new ByteArrayInputStream('test'.getBytes('utf-8'))).doUpload()
        sleep(DEFAULT_SLEEP)
        json = getLoopbackBuffer()
        then:
        assert json instanceof java.util.List
        json.size() == 1
        json[0].artifactory.webhook.data.createdBy == "admin"
        json[0].artifactory.webhook.data.name == "file4.txt"
        json[0].artifactory.webhook.data.repoKey == MAVEN_REPO1_NAME

        cleanup:
        Control.deleteFolder(BASE_PORT, WEBHOOK_CONFIG_PATH)
    }

    def 'test enabled'() {
        setup:
        clearLoopbackBuffer()

        // 1. Test the default (true) enabled setting
        when:
        def reload = reloadAndVerifyConfig('''{
          "webhooks": {
              "test_webhook": {
                  "url": "http://localhost:8081/artifactory/api/plugins/execute/webhookLoopback",
                  "events": [
                    "execute.pingWebhook"
                  ]
              }
          }
        }
        ''')
        reload.code == SUCCESS_CODE && reload.response.contains("Reloaded")
        getRequest("${BASE_URL}/${WEBHOOK_PING_URL}")
        sleep(DEFAULT_SLEEP)
        def json = getLoopbackBuffer()
        then:
        assert json instanceof java.util.List
        json.size() == 1
        json[0].artifactory.webhook.data.message == "It works!"


        // 2. Test the enabled = false
        when:
        clearLoopbackBuffer()
        reload = reloadAndVerifyConfig('''{
          "webhooks": {
              "test_webhook": {
                  "url": "http://localhost:8081/artifactory/api/plugins/execute/webhookLoopback",
                  "events": [
                    "execute.pingWebhook"
                  ],
                  "enabled": false
              }
          }
        }
        ''')
        reload.code == SUCCESS_CODE && reload.response.contains("Reloaded")
        getRequest("${BASE_URL}/${WEBHOOK_PING_URL}")
        sleep(DEFAULT_SLEEP)
        json = getLoopbackBuffer()
        then:
        assert json instanceof java.util.List
        json.size() == 0

        // 3. Test the explicit enabled = true
        when:
        clearLoopbackBuffer()
        reload = reloadAndVerifyConfig('''{
          "webhooks": {
              "test_webhook": {
                  "url": "http://localhost:8081/artifactory/api/plugins/execute/webhookLoopback",
                  "events": [
                    "execute.pingWebhook"
                  ],
                  "enabled": true
              }
          }
        }
        ''')
        reload.code == SUCCESS_CODE && reload.response.contains("Reloaded")
        getRequest("${BASE_URL}/${WEBHOOK_PING_URL}")
        sleep(DEFAULT_SLEEP)
        json = getLoopbackBuffer()
        then:
        assert json instanceof java.util.List
        json.size() == 1
        json[0].artifactory.webhook.data.message == "It works!"


        cleanup:
        Control.deleteFolder(BASE_PORT, WEBHOOK_CONFIG_PATH)

    }



    /**
     * Clears the loopback endpoint
     */
    static def clearLoopbackBuffer() {
        callPost("${BASE_URL}/${WEBHOOK_LOOPBACK_CLEAR_URL}","")
    }

    /**
     * Get the stored POSTS that have been made to the loopback endpoint
     */
    static def getLoopbackBuffer() {
        def result = getRequest("${BASE_URL}/${WEBHOOK_LOOPBACK_GET_URL}")
        if (result.response != null)
            return new JsonSlurper().parseText(result.response)
        return null
    }


    static def getRequest(String urlString) {
        def request = new URL(urlString).openConnection()
        request.doOutput = true
        request.setRequestProperty("Authorization", "Basic ${'admin:password'.bytes.encodeBase64().toString()}")
        try {
            def code = request.getResponseCode()
            def response = request.getInputStream().getText()
            return [response: response, code: code]
        } catch (ex) {
            return [response: null, code: null]
        }
    }

    static def callPut(String urlString, String content, headers=null) {
        callWrite(urlString, content, "PUT", headers)
    }

    static def callPost(String urlString, String content, headers=null) {
        callWrite(urlString, content, "POST", headers)
    }

    static def callWrite(String urlString, String content, String method, Map<String, String> headers=null) {
        def write = new URL(urlString).openConnection()
        write.setRequestProperty("Authorization", "Basic ${'admin:password'.bytes.encodeBase64().toString()}")
        if (headers != null) {
            headers.each { k, v ->
                write.setRequestProperty(k, v)
            }
        }
        write.method = method
        write.doOutput = true
        def writer = null, reader = null
        try {
            writer = write.outputStream
            writer.write(content.getBytes("UTF-8"))
            writer.flush()
            def postRC = write.getResponseCode()
            reader = write.inputStream
            def response = reader.text
            return [response: response, code: postRC]
        } catch (ex) {
            return [response: null, code: null]
        } finally {
            if (writer != null)
                writer.close()
            if (reader != null)
                reader.close()
        }
    }

}
