Validate Client Checksums
=======================================

This plugin rejects downloads of artifacts which don't have a client-published checksum value 

Files uploaded via the REST API will not have an original checksum -- clicking the Download button in the UI will trigger a 409 error

Installation
=======================================

*Place the script in the `${ARTIFACTORY_HOME}/etc/plugins` folder
*Reload your plugin with curl by running 'http://SERVER:PORT/artifactory/api/plugins/reload' from your terminal