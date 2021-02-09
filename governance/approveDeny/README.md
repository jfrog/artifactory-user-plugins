Artifactory Authenticate Entitlements User Plugin
=======================================

This plugin authenticates entitlements

Installation
---------------------

1. Create the directory ``/opt/jfrog/artifactory/var/etc/artifactory/plugins/lib`` and copy the three libraries there:

  * [HTTPBuilder](https://bintray.com/bintray/jcenter/org.codehaus.groovy.modules.http-builder%3Ahttp-builder/_latestVersion)
  * [Json-lib](https://bintray.com/bintray/jcenter/net.sf.json-lib%3Ajson-lib/_latestVersion)
  * [Xml-resolver](https://bintray.com/bintray/jcenter/xml-resolver%3Axml-resolver/_latestVersion)

1. Copy this plugin to the **plugins** directory:

  ```
  /opt/jfrog/artifactory/var/etc/artifactory/plugins/approveDeny.json
  /opt/jfrog/artifactory/var/etc/artifactory/plugins/approveDeny.groovy
  ```
1. Load the plugin:

  ```
  curl -u admin:password -XPOST -H'Content-Type: application/json' http://localhost:8082/artifactory/api/plugins/reload
  ```
