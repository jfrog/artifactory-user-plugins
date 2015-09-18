Artifactory Execute From Filestore User Plugin
==============================================

When called from the REST API, this plugin will copy a specified file tree from
the Artifactory server to the local filesystem, and execute a specified command
on that file tree. The executed command takes the form of a `find` call at the
location of the copied file tree, with custom arguments.

Executing
---------

To execute the plugin:

`curl -T execCommand.json -X POST -v -u admin:password "http://localhost:8080/artifactory/api/plugins/execute/copyAndExecute"`

The `execCommand.json` file is a JSON file with the following fields:

- `srcRepo`: The Artifactory repository to copy from
- `srcDir`: The Artifactory directory path to copy
- `destLocalDir`: The local filesystem path to copy the file tree into
- `params`: Any parameters to pass to the `find` command
