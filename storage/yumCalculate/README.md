Artifactory YUM Calculate User Plugin
=====================================

This plugin synchronously calculates the YUM metadata at a given repository
path.

Executing
---------

To execute this plugin:

`curl -X POST -u admin:password http://localhost:8088/artifactory/api/plugins/execute/yumCalculate?params=path=yum-repository/path/to/dir`
