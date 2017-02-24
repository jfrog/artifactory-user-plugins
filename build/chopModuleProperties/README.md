Artifactory Chop Module Properties User Plugin
==============================================

*This plugin is currently only working in Artifactory 4.x. We are working on updating it to work in Artifactory 5.x.*

Whenever a build is submitted, this plugin ensures that all of the property
values of all of the build's modules are under 900 characters in length. If any
of the property values are 900 characters or over, this plugin chops them down
to 899 characters.