Artifactory Layout Properties User Plugin
=========================================

*This plugin is currently being tested for Artifactory 5.x releases.*

This plugin runs whenever an aritifact is deployed. It takes all the tokens from
your layout (such as `baseRev`, `fileItegRev`, `module`, `orgPath` etc) and
creates properties prefixed with a fixed prefix (by default `layout.`). It also
creates properties for any custom tokens you might create.