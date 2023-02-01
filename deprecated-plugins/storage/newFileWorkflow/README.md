Artifactory New File Workflow Plugin
=============================================

This plugin checks newly uploaded artifacts for something specific defined in `dummyExecute`. Before a new artifact has been checked, the property `workflow.status` is set to `NEW`. While the new artifact is being checked, the property is changed to ```PENDING```. Then, the property is set to `EXECUTED` or `FAILED_EXECUTION` depending on what the artifact was checked for. The plugin executes every 20 seconds.

The default function `dummyExecute` checks whether the file contains an `'A'`. The property `workflow.status` is set to `EXECUTED` if the file did not have an A in it, and `FAILED_EXECUTION` if the file contained an A.

Installation
------------

To install this plugin:

1. Place this script under the master Artifactory server
   `${ARTIFACTORY_HOME}/etc/plugins`.
2. Verify in the `${ARTIFACTORY_HOME}/logs/artifactory.log` that the plugin loaded
   correctly.