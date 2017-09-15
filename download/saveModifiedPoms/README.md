Artifactory Save Modified Poms User Plugin
==========================================

This plugin backs up modified versions of pom files.

Workflow
--------

This plugin assumes a particular repository setup:

- A virtual repository that proxies requests for artifacts.
- A number of local repositories, accessible via the virtual, acting as original
  sources for artifacts.
- A local repository called `fmw-virtual`, also accessible via the virtual,
  which contains overrides for artifacts found in the source repositories.
- A local repository called `saved-poms`, which will be populated with backup
  pom files by this plugin.

Either of the above named repositories (`fmw-virtual` and `saved-poms`) can be
named anything, but the names listed for these repositories in
`saveModifiedPoms.groovy` must be changed to match.

Usage
-----

This plugin will automatically run every five minutes, and can also be run on
demand via a REST endpoint, like so:

``` shell
curl -XPOST -uadmin:password http://localhost:8081/artifactory/api/plugins/execute/testCopyModifiedPoms
```

When run, this plugin will look for any modified pom files in `fmw-virtual`, and
copy them into `saved-poms`.

Installation
------------

To install, simply drop `saveModifiedPoms.groovy` into your
`$ARTIFACTORY_HOME/etc/plugins/` directory, and change the repository names on
lines 25 and 26, if applicable. Use the [reload plugins REST endpoint][1] to
load the plugin.

[1]: https://www.jfrog.com/confluence/display/RTF/Artifactory+REST+API#ArtifactoryRESTAPI-ReloadPlugins
