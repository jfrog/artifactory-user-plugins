Artifactory Expose Filestore User Plugin
========================================

This plugin exposes the contents of an Artifactory repository to a given
location on the local filesystem. It does this by recreating the directory
structure of the repository, and creating symbolic links to the binaries in the
filestore. This plugin will only link files with a property `expose` set to
`true`, and will ignore all other files.

Parameters
----------

This plugin takes two parameters:

- repo: The name of the Artifactory repository to expose
- dest: The path to the local destination directory to expose to

For example:

`curl -X POST -v -u admin:password "http://localhost:8080/artifactory/api/plugins/execute/exposeRepository?params=repo=repoKey|dest=destFolder"`
