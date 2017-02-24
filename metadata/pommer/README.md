Artifactory Pommer User Plugin
==============================

*This plugin is currently only working in Artifactory 4.x. We are working on updating it to work in Artifactory 5.x.*

This plugin autogenerates `.pom` files for all artifacts that don't have them
already. This can be useful, for example, when running a version search on a
very large repository: since metadata searches are much faster than full
database searches, any artifact with a `.pom` file will be found much more
quickly by the search.

This plugin only creates `.pom` files for artifacts in local Maven 2
repositories, which conform to the Maven 2 layout.

Whenever an artifact is deployed, a `.pom` file will automatically be generated
if one doesn't already exist. Also, a REST endpoint `pommify` is defined that
allows `.pom` files to be generated for all artifacts located in a provided set
of repositories. For example:

``` shell
curl -v -XPOST -uadmin:password "http://localhost:8081/artifactory/api/plugins/execute/pommify?params=repos=ext-release-local,libs-release-local"
```
