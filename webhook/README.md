Artifactory Webhook Plugin
===============================

This plugin provides a webhook post to a remote service.  The plugin consists of 2 components:
1. webhook.groovy - the actual artifactory plugin
2. webhook.config.json - configuration file for setting url and events for activation


Installation
-----------------
1. Copy webhook.config.json.sample to webhook.config.json, configure and copy to ARTIFACTORY_HOME/etc/plugins
2. Copy webhook.groovy to ARTIFACTORY_HOME/etc/plugins

Configuration
-----------------
The webhook.config.json allows the configuration of multiple webhooks and has various webhook specific and global options.
A particular webhook only requires a URL and the events it should listen to. This is the simplest possible configuration file:

```json
{
  "webhooks": {
      "mywebhookname": {
          "url": "http://example.com",
          "events": [
            "storage.afterCreate"
          ]
      }
  }
}
```

### Webhook properties
| Property      | Description   | Required  | Default |
| ------------- |-------------| ---------| -------|
| url     | The URL to POST to | true      | -       |
| events      | The events to listen to      | true      | -       |
| repositories | The list of repositories to limit the event listening to. Only applies to storage and docker events. | False     | * (all repositories)   |
| path | A path that must match for the event to be triggered. Accepts wildcard '*'. Do not include repository name in path. Only applies to storage and docker events.| False     | * (all paths)   |
| async | Whether the POST call should be asynchronous      | false     | true       |
| enabled | Whether this webhook should be enabled     | false     | true       |
| format | The formatting of the message     | false     | default       |

### Global properties
| Property      | Description   | Required  | Default |
| ------------- | ------------- | --------- | ------- |
| debug     | Additional logging | false      | false       |
| timeout      | Timeout for POST call      | false      | 15000 (ms)  |
| baseUrl      | Base URL of Artifactory instance. Only applies to Spinnaker format      | false      | -  |

#### Making changes to the configuration

You can make changes to the configuration without having to reload the plugin by making the following request:

`curl -XPOST -u [admin-username]:[admin-password] [artifactory-url]/api/plugins/execute/webhookReload`


#### Detailed Sample Configuration
Here is an example of configuration file using all the bells and whistles:

```json
{
  "webhooks": {
      "slack": {
        "url": "https://hooks.slack.com/services/######/######/#######",
        "events": [
          "execute.pingWebhook",
          "docker.tagCreated",
          "docker.tagDeleted"
        ],
        "format": "slack"
    },
    "docker": {
      "url": "http://example2.com",
      "events": [
        "docker.tagCreated",
        "docker.tagDeleted"
      ],
      "repositories": [
        "docker-local"
      ],
      "path": "example:1.0.1"
    },
    "audit": {
      "url": "http://example3.com",
      "events": [
        "storage.afterCreate",
        "storage.afterDelete",
        "storage.afterPropertyCreate"
      ],
      "async": true,
      "repositories": [
        "generic-local"
      ],
      "path": "archive/contrib/*"
    },
    "allStorageAndReplication": {
      "url": "http://example4.com",
      "events": [
        "storage.afterCreate",
        "storage.afterDelete",
        "storage.afterMove",
        "storage.afterCopy",
        "storage.afterPropertyCreate",
        "storage.afterPropertyDelete"
      ],
      "enabled": false
    }
  },
  "debug": false,
  "timeout": 15000
}
```

Supported Events
-----------------

### Storage
* storage.afterCreate - Called after artifact creation operation
* storage.afterDelete - Called after artifact deletion operation
* storage.afterMove - Called after artifact move operation
* storage.afterCopy - Called after artifact copy operation
* storage.afterPropertyCreate - Called after property create operation
* storage.afterPropertyDelete - Called after property delete operation

### Build
* build.afterSave - Called after a build is deployed

### Execute
* execute.pingWebhook - Simple test call used to ping a webhook endpoint

### Docker
* docker.tagCreated - Called after a tag is created
* docker.tagDeleted - Called after a tag is deleted

Webhook Formatters
-----------------

* default - The default formatter
* keel - A POST formatted specifically for keel.sh
* slack - A POST formatted specifically for Slack
* spinnaker - A POST formatted specifically for Spinnaker

### Using the keel format

In order to work with keel format, you need to set your docker registry url in your config file
( to be prepended to the image name + tag in the webhook)

```json
{
  "webhooks": {
    "keel": {
        "url": "https://keel.example.com/v1/webhooks/native",
        "events": [
          "docker.tagCreated"
        ],
        "format": "keel"
    }
  },
  "dockerRegistryUrl": "docker-registry.example.com"
}

```

#### Using the slack format

In order to work with Slack POST hooks, you need to add the [incoming-webhook app](https://api.slack.com/incoming-webhooks).
This will generate a look with the format 'https://hooks.slack.com/services/######/######/#######' which you will use as
the **url** in the configuration file. See the detailed sample configuration above.

#### Using the spinnaker format

In order to work with Spinnaker POST hooks, you need to enable spinnaker support and set the base url.
This will generate a look with the format 'https://www.spinnaker.io/reference/artifacts/#format' which you will use as
the **url** in the configuration file. See the detailed sample configuration below.

```json
{
  "webhooks": {
    "helm": {
      "url": "SPINNAKER WEBHOOK URL for HELM",
      "events": [
        "storage.afterCreate"
      ],
      "path": "*.tgz",
      "format": "spinnaker"
    },
    "docker": {
      "url": "SPINNAKER WEBHOOK URL for Docker",
      "events": [
        "docker.tagCreated"
      ],
      "format": "spinnaker"
    }
  },
  "debug": false,
  "timeout": 15000,
  "baseurl": "Artifactory base URL -- http://localhost:8081/artifactory"
}
```



Copyright &copy; 2011-, JFrog Ltd.

