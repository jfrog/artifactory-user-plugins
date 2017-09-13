Artifactory Add PyPi Normalized Name User Plugin
================================================

This Artifactory User Plugin is for Pypi packages in version 1.2 that aren't
correctly generating the `pypi.normalized.name` property, despite having the
correct metadata file(s).

It will only work in Pypi local repositories, with artifacts that already have a
`pypi.name` property, but not a `pypi.normalized.name`.

Installation
------------

Place the `PypiPropertyPlugin.groovy` file into the
`$ARTIFACTORY_HOME/etc/plugins/` folder. You will need to restart Artifactory
unless you have it set to scan for plugin changes. That can be done by setting
the following in `$ARTIFACTORY_HOME/etc/artifactory.system.properties`:

```
artifactory.plugin.scripts.refreshIntervaleSecs= [value greater than 0]
```

To enable logging, find the logback file located at
`$ARTIFACTORY_HOME/etc/logback.xml` and add the following lines at the bottom:

``` xml
<logger name = "pypiPropertyPlugin">
  <level value = "info">
</logger>
```

You should be able to see the script logging start in `artifactory.log`.

Usage
-----

By default, the plugin runs every 30 seconds. This can be changed by editing the
`interval` parameter on line 13:

``` groovy
addPerms(interval: 30000, delay: 100) {
```

Add the Pypi repositories that you wish to have monitored by the script on line
8, by modifying the provided array `searchRepoNames`.

``` groovy
def searchRepoNames = []
```

Please note that if new repositories are added, the script must be reloaded
before it can detect them for property addition.
