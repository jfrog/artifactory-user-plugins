Artifactory beforeDownloadRequest Sample User Plugin
====================================================

A sample implementation of the beforeDownloadRequest execution point. This
plugin causes JSON files to expire and be recached when downloaded from a remote
repository, if the file has been in the cache for over a certain period of time, by default set to an hour.

## Features

By modifying the plugin you can also change the file type you want to affect, and your desired expiration time.

## Installation

* Place script under your plugins folder at `${ARTIFACTORY_HOME}/etc/plugins/`
* Restart your artifactory instance or [use the API to reload plugins][1]



[1]:https://www.jfrog.com/confluence/display/RTF/User+Plugins#UserPlugins-ReloadingPlugins

