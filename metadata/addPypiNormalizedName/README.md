This Artifactory User Plugin is for Pypi packages in version 1.2 that aren't correctly generating the pypi.normalized.name property, despite having the correct metadata file(s).

It will only work in Pypi Local Repositories, with artifacts that already have a pypi.name property, but not a pypi.normalized.name.

This plugin has been tested in Artifactory Pro 5.2.x and 4.9.x using the supplied testing file.

Installation:

Place the PypiPropertyPlugin.groovy file into the $ARTIFACTORY_HOME/etc/plugins/ folder. You will need to restart Artifactory unless you have it set to scan for plugin changes. That can be done by setting

artifactory.plugin.scripts.refreshIntervaleSecs= [value greater than 0]

in $ARTIFACTORY_HOME/etc/artifactory.system.properties.

To enable logging, find the logback file located at $ARTIFACTORY_HOME/etc/logback.xml

and add the following lines at the bottom:

<logger name = "pypiPropertyPlugin">
  <level value = "info">
</logger>
You should be able to see the script logging start in Artifactory.log.

Usage:

By default, it runs every 30 seconds, this can be changed by editing the interval parameter interval on line 7.

addPerms(interval: 30000, delay: 100) {

Add the Pypi repositories that you wish to have monitored by the script on line 10, by adding it to the provided array searchRepoNames.

def searchRepoNames = []

Please note that if new repositories are added, the script must be reloaded before it can detect them for property addition.

Testing:

Testing was done using the Pypi package binparse, but can be substituted for any valid Pypi Package. Testing has been provided in the pypiPropertyPluginTest.groovy file.
