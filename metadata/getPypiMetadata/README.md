Artifactory Get Pypi Metadata User Plugin
=========================================

This execution is named 'getPypiMetadata' and it will be called by REST by this
name. Map parameters provide extra information about this execution, such as
version, description, users that areallowed to call this plugin, etc. The
expected (and mandatory) parameter is a Pypi repository/file path from which
metadata will be extracted.

Example execution:

`curl -X POST -v -u admin:password "http://localhost:8081/artifactory/api/plugins/execute/getPypiMetadata?params=repoPath=/3.3/s/six/six-1.9.0-py2.py3-none-any.whl|repoKey=pypi-remote-cache"`
