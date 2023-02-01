Artifactory Conda User Plugin
=============================

This plugin adds basic support for [Conda](https://conda.io/docs/) repositories.
## __*** Deprecation NOTICE ***__

*[Artifactory 6.3](https://www.jfrog.com/confluence/display/RTF/Release+Notes#ReleaseNotes-Artifactory6.3) introduced [Conda Repositories](https://www.jfrog.com/confluence/display/RTF/Conda+Repositories).  This plugin is now deprecated and will no longer be tested*

*Documentation and plugin is preserved here for users of older versions.*
Usage
-----

To create a Conda repository, create a generic local or remote repository, and
add a property "conda" to the root (the value of the property does not matter).

Local Conda repositories will be indexed automatically. This means that packages
will be moved to the correct locations, and metadata files (repodata.json and
repodata.json.bz2 files) will be generated and kept up to date. Reindexing
occurs every 10 seconds.

Remote Conda repositories will not serve cached metadata files, and will instead
always pull the most recent metadata from upstream.

This plugin does not support virtual Conda repositories.
