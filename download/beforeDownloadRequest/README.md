Artifactory beforeDownloadRequest Sample User Plugin
====================================================

*This plugin is currently being tested for Artifactory 5.x releases.*

A sample implementation of the beforeDownloadRequest execution point. This
plugin causes JSON files to expire and be recached when downloaded from a remote
repository, if the file has been in the cache for over a certain period of time, by default set to an hour.

## Features

By modifying the plugin you can also change the file type you want to affect, and your desired expiration time.

## Installation

* Place script under your plugins folder at `${ARTIFACTORY_HOME}/etc/plugins/`
* Restart your artifactory instance or [use the API to reload plugins][1]
* Test out manually by following the next steps:
    * Create a test local repository
    * Create a remote repository that links to the local one
    * Create a sample  test.json file to upload (or any other format specified in the plugin)
    * Upload the file to your local repository.
    * Download the file through your remote repository.
    * The checksum value of the file in local repository should be the same as the one for the file in 
    remote-cache repository.
    * Change the contents of test.json, upload to local, and download through remote again.
        * Take less than an hour, or the indicated expiry time, in between downloads
    * The checksum value of the file in local and the file you downloaded, or the one in remote-cache, 
    should be different.
    * Wait the specified time (one hour by default) and try downloading the same file from remote again 
    (without any changes this time) 
    * Now the checksum value of the downloaded file, the file on remote-cache, and the local file should 
    be the same
    

[1]:https://www.jfrog.com/confluence/display/RTF/User+Plugins#UserPlugins-ReloadingPlugins

