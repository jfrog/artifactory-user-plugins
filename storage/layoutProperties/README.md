Artifactory Layout Properties User Plugin
=========================================

This plugin runs whenever an aritifact is deployed. It takes all the tokens from
your layout (such as `baseRev`, `fileItegRev`, `module`, `orgPath` etc) and
creates properties prefixed with a fixed prefix (by default `layout.`). It also
creates properties for any custom tokens you might create.

For example, when an artifact is deployed to a path
`com/google/guava/guava/10.0.1/guava-10.0.1.jar` in a Maven 2 repository, it
will be given the following properties:

```
layout.organization: com.google.guava
layout.module: guava
layout.baseRevision: 10.0.1
layout.ext: jar
```
