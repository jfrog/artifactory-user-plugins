Artifactory Sample User Plugins
===============================

### Note : Many of the artifactory user plugins are no longer compatible with the recent versions of Artifactory particularly after JDK17 support introduced in 7.43 and above.These deprecated plugins have been moved to the [deprecated directory] .**

[deprecated directory]: http://github.com/jfrog/artifactory-user-plugins/tree/master/deprecated-plugins


A collection of sample [groovy user plugins] for [Artifactory].

To contribute, please see [CONTRIBUTING.md](CONTRIBUTING.md).

Copyright &copy; 2011-, JFrog Ltd.

[Artifactory]: http://artifactory.jfrog.org
[groovy user plugins]: http://wiki.jfrog.org/confluence/display/RTF/User+Plugins

**Upgrade Notice:** Artifactory Plugins Upgraded to Groovy 4 in Artifactory Version 7.101.0<br>
Artifactory has been upgraded to Groovy 4. Please note that the promotion plugin has been upgraded in order to be compatible with Groovy version upgrade. Upgrading this plugin requires moving to Groovy 4, which may also affect User Plugins.<br>
**Which Branch to use?**<br>
**master:** For development with Groovy 4.<br>
**artifactory-java-lower-than-21:** For development before the Groovy 4 upgrade.<br>

Please update your development environment to maintain compatibility with the new Java and Groovy versions.
