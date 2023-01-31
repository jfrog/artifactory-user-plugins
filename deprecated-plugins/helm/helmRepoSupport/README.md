Artifactory Helm Repo Support User Plugin
=========================================

**This plugin is deprecated as of Artifactory v5.8.0 and will not be tested against newer versions. Instead you should use the built-in support to Helm repositories. For more information on how to configure Helm repositories in Artifactory check the [wiki page](https://www.jfrog.com/confluence/display/RTF/Helm+Chart+Repositories).**

HelmRepoSupport is only supported from version 4.x

HelmRepoSupport is a user plugin for Artifactory.

HelmRepoSupport allows your Generic remote repository to act as Helm repository to proxy/cache Helm Charts.

Note: Default it will only work http://storage.googleapis.com/kubernetes-charts. If you want to use other Repository as endpoint change it in helmRepoSupport.groovy.

Features
--------

- Cache Index.yaml
- Cache charts archive.
- Rewrite archive urls in index.yaml to artifactory repo urls.

Installation
------------

To install HelmRepoSupport:

1. Download the following dependency jars, and put them in
   `${ARTIFACTORY_HOME}/etc/plugins/lib`:
   * [YamlBeans](https://bintray.com/bintray/jcenter/com.esotericsoftware.yamlbeans%3Ayamlbeans/1.06#files)

2. Place `helmRepoSupport.groovy` file under `${ARTIFACTORY_HOME}/etc/plugins`.

3. Reload plugin using [Rest API] (https://www.jfrog.com/confluence/display/RTF/Artifactory+REST+API#ArtifactoryRESTAPI-ReloadPlugins)

4. Verify in the `${ARTIFACTORY_HOME}/logs/artifactory.log` that the plugin
   loaded the configuration correctly.
   
