# Artifactory modifyNuGetDownlaod User Plugin
====================================================
*This plugin was tested for Artifactory 4.x releases.*
*This plugin was tested for Artifactory 5.x releases.*

## Features
====================================================
This plugins rewrite downlaod request for nupkg packages in nuget-gallery repo 2 levels down from it's original request path

## Installation
====================================================

To install pluging, put your groovy file under `${ARTIFACTORY_HOME}/etc/plugins` and restart your artifactory instance

You can enable autoreload at `${ARTIFACTORY_HOME}/etc/artifactory.system.properties` by changing this property:


        artifactory.plugin.scripts.refreshIntervalSecs=0
