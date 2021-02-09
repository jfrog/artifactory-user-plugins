Artifactory Authenticate Entitlements User Plugin
=======================================

This plugin authenticates entitlements

Testing
---------------------

Here are the steps to setup and test this plugin locally.

Requrements:
  1. Docker
  1. Python3
  1. curl
  1. [JFrog CLI](https://jfrog.com/getcli/)


Setup
---------------------
1. Clone the repository and switch branches:

  ```
  git clone git@github.com:jfrog/artifactory-user-plugins.git
  cd artifactory-user-plugins/governance/approveDeny
  ```

1. Create the ``lib`` directory:

  ```
  mkdir lib
  ```

  And download these libraries and put them in the ``lib`` directory just created:

  * [HTTPBuilder](https://bintray.com/bintray/jcenter/org.codehaus.groovy.modules.http-builder%3Ahttp-builder/_latestVersion)
  * [Json-lib](https://bintray.com/bintray/jcenter/net.sf.json-lib%3Ajson-lib/_latestVersion)
  * [Xml-resolver](https://bintray.com/bintray/jcenter/xml-resolver%3Axml-resolver/_latestVersion)

  The ``beforeDebug.sh`` script will put these libraries where they need to be in the Docker container.

1. Open a second terminal to the ``approveDeny`` folder and run a debuggable Artifactory Docker container:

  ```
  bash scripts/rundocker.sh
  ```

1. Install the license file:

  ```
  bash scripts/setupdocker.sh <PATH_TO_LICENSE_FILE>
  ```

1. Setup IntelliJ:

  1. File | Open and browse to the **approveDeny** plugin directory.
  1. Run | Edit Configurations
    1. Add New Configurations
    1. Choose **Remote JVM Debug**
    1. Change the port to 5555
    1. Press OK
    1. In the before launch list, click the + to add
    1. Choose **Run External Tool**
    1. Name: Push Changes
      Program: bash
      Arguments: deploy.sh
      Working directory: $ProjectFileDir$

1. Logs are very important when debugging and will provide valuable information.
The log files are located in the ``/var/opt/jfrog/artifactory``. The most useful
one for plugins is ``artifactory-service.log``. There are two ways to get the logs:

    1. SSH into the Docker container and look at the file:
      ```
      docker exec -it rt1 sh
      cat /opt/jfrog/artifactory/var/log/artifactory-service.log
      ```

    1. Use VS Code and the Docker plugin, search for your container in the docker
    plugin and browse to ``/var/opt/jfrog/artifactory/var/log/artifactory-service.log``.
    Right click on the file and choose **open**. It will be automatically refresh
    when you select the VS Code window.

The only way to trigger the plugin code is to call a REST API call.

1. Run the test IP verification and entitlements server:

    ```
    python3 unittest/testserver.py
    ```
    Make sure the test server is working:

      ```
      curl http://localhost:8888
      ```

    Use VS Code with the Microsoft Python plugin to debug ``testserver.py`` and
    verify the API is correct.

Debugging
---------------------

Setup Artifactory to test the **approveDeny** plugin:

1. Attach the debugger to the Artifactory process **^D**.
1. Set a breakpoint in the plugin code.
1. Attempt to download the jar from Artifactory:

  ```
  bash test.sh
  ```

A manual test can be invoked with:

  ```
  curl -u admin:password -XPOST -H'Content-Type: application/json' "http://localhost:8082/artifactory/api/plugins/execute/test?params=key=value"
  ```

1. Debugging:
  ^D debug
  F8 step
  F7 step into
  F9 resume

  Because Artifactory is running within Tomcat and the plugin is running inside of
  Artifactory, once the function you are debugging is complete you must choose **resume**
  or the process will appear to hang.


Useful Commands
---------------------

Sometimes Artifactory becomes unresponsive:

  ```
  docker restart rt1
  docker container logs --follow rt1
  ```

Remove Artifactory docker container:

  ```
  docker rm --force rt1
  ```
