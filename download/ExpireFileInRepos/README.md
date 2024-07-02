Artifactory expireFileInRepos User Plugin
====================================================

An implementation of the beforeDownloadRequest execution point. This
plugin causes artifacts (including binaries) to expire and be re-cached when downloaded from a remote/virtual
repository.

## Features

To use the plugin, modify the script by adding the names of the repositories you wish to expire artifacts in. 
The names are to be added to the list 'reposToExpire'.
Note: using this plugin, when downloading artifacts from virtual repositories - artifacts are expired and served from 
the source repo for all underlying repos

## Installation

* Place script under your plugins folder at `${ARTIFACTORY_HOME}/etc/plugins/`
* Restart your artifactory instance or [use the API to reload plugins][1]



[1]:https://www.jfrog.com/confluence/display/RTF/User+Plugins#UserPlugins-ReloadingPlugins

