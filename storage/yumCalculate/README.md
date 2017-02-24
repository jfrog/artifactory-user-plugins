Artifactory YUM Calculate User Plugin
=====================================

*This plugin is currently only working in Artifactory 4.x. We are working on updating it to work in Artifactory 5.x.*

This plugin synchronously calculates the YUM metadata at a given repository
path.

Executing
---------

To execute this plugin:

`curl -X POST -u admin:password http://localhost:8088/artifactory/api/plugins/execute/yumCalculate?params=path=yum-repository/path/to/dir`
