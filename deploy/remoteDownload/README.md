<h1> remoteDownload User Plugin </h1>

This plugin recieves a remote file URL and deploys the file into Artifactory.

<h2> Dependencies </h2>

The plugin requires two third-party libraries in order to run:

http-builder
json-lib

To install the dependencies, create the $ARTIFACTORY_HOME/etc/plugins/lib directory, and put the above two jars in it.

<h2> Logging </h2>

To enable logging for the plugin, add the below logger to your $ARTIFACTORY_HOME/logback.xml:

`<logger name="remoteDownload">
    <level value="info"/>
  </logger>`
  
