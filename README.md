# Artifactory User Plugins

> **Note**: JFrog Workers is the recommended cloud-native solution for extending the JFrog Platform (including Artifactory). While user plugins are still supported, we recommend using JFrog Workers where possible for better scalability, security, and performance. [Learn more about JFrog Workers](https://jfrog.com/help/r/ybbUNZGwwAmzW2qGyL9Zdw/I4E5sOhWWpIHHfdV37__Iw).

## Upgrade Notice

1. **Groovy 4 Upgrade**: Artifactory is upgraded to Groovy 4 in version *7.101.0* (released on 25th Nov 2024 for cloud), which leads user plugins compatible with groovy 4. The promotion plugin has been updated for compatibility with Groovy 4. This upgrade may affect existing user plugins, so ensure you test your plugins after upgrading.

   - **Branch Compatibility**:
     - **master**: Artifactory 7.101.0 & above
        > **Note**: Groovy 3 no longer supported on master branch
     - **artifactory-groovy-3**: Artifactory 7.100.0 & below

1. **JDK17 Compatibility**: With the introduction of JDK17 support in Artifactory version **7.43** and above, many older user plugins may no longer be compatible. Deprecated plugins are now moved to the [deprecated directory](http://github.com/jfrog/artifactory-user-plugins/tree/master/deprecated-plugins).

Please consider migrating to JFrog Workers for cloud-native and future-proof solutions.


## Documentation References
- [Artifactory Documentation](https://jfrog.com/help/p/devops-home): Official documentation for Artifactory features, setup, and configuration.
- [User Plugin Documentation](https://jfrog.com/help/r/jfrog-integrations-documentation/user-plugins): Guide to writing and using custom Artifactory plugins.
- [JFrog Workers Documentation](https://jfrog.com/help/r/ybbUNZGwwAmzW2qGyL9Zdw/I4E5sOhWWpIHHfdV37__Iw): Learn more about JFrog Workers for extending Artifactory in a cloud-native way.

## Worker Alternatives for Plugins
JFrog Workers are supported only for **Enterprise X** and **Plus** licenses. Workers offer an alternative to Artifactory user plugins, enabling you to extend the JFrog Platform using cloud-native, scalable, and secure solutions.

The following are the Worker alternatives for Artifactory plugins:

| User Plugins | Worker Alternative |
|-------------|--------------------|
| [artifactCleanup](https://github.com/jfrog/artifactory-user-plugins/blob/master/cleanup/artifactCleanup/README.md) | [ArtifactCleanup Worker](https://github.com/jfrog/workers-sample/tree/main/samples/artifactory/GENERIC_EVENT/artifact-cleanup) |
| [backUpFolder](https://github.com/jfrog/artifactory-user-plugins/blob/master/backup/backupFolders/README.md) | X |
| [promotions](https://github.com/jfrog/artifactory-user-plugins/blob/master/build/promotion/README.md) | X |
| [checksums](https://github.com/jfrog/artifactory-user-plugins/blob/master/checksums/README.md) | X |
| [cleanDockerImages](https://github.com/jfrog/artifactory-user-plugins/blob/master/cleanup/cleanDockerImages/README.md) | [cleanDockerImages Worker](https://github.com/jfrog/workers-sample/blob/main/samples/artifactory/GENERIC_EVENT/clean-docker-images/README.md) |
| [deleteByPropertyValue](https://github.com/jfrog/artifactory-user-plugins/blob/master/cleanup/deleteByPropertyValue/README.md) | [deleteByPropertyValue Worker](https://github.com/jfrog/workers-sample/blob/main/samples/artifactory/GENERIC_EVENT/delete-by-property-value/README.md) |
| [deleteDeprecatedPlugin](https://github.com/jfrog/artifactory-user-plugins/blob/master/cleanup/deleteDeprecated/README.md) | X |
| [deleteEmptyDirsPlugin](https://github.com/jfrog/artifactory-user-plugins/blob/master/cleanup/deleteEmptyDirs/README.md) | [deleteEmptyDirsWorker](https://github.com/jfrog/workers-sample/blob/main/samples/artifactory/GENERIC_EVENT/delete-empty-dirs/README.md) |
| [mavenSnapshotCleanupWhenRelease](https://github.com/jfrog/artifactory-user-plugins/blob/master/cleanup/mavenSnapshotCleanupWhenRelease/README.md) | X |
| [cleanOldBuilds](https://github.com/jfrog/artifactory-user-plugins/blob/master/cleanup/oldBuildCleanup/README.md) | X |
| [remoteBackup](https://github.com/jfrog/artifactory-user-plugins/blob/master/storage/remoteBackup/README.md) | [remoteBackup Worker](https://github.com/jfrog/workers-sample/blob/main/samples/artifactory/GENERIC_EVENT/remote-backup/README.md) |
| [repoQuota](https://github.com/jfrog/artifactory-user-plugins/blob/master/storage/repoQuota/README.md) | [repoQuota Worker](https://github.com/jfrog/workers-sample/blob/main/samples/artifactory/BEFORE_UPLOAD/repo-quota/README.md) |
| [restrictOverwrite](https://github.com/jfrog/artifactory-user-plugins/blob/master/storage/restrictOverwrite/README.md) | [restrictOverwrite Worker](https://github.com/jfrog/workers-sample/blob/main/samples/artifactory/BEFORE_UPLOAD/restrict-overwrite/README.md) |
| [repoStats](https://github.com/jfrog/artifactory-user-plugins/blob/master/stats/repoStats/README.md) | [repoStats Worker](https://github.com/jfrog/workers-sample/blob/main/samples/artifactory/GENERIC_EVENT/repoStats/README.md) |
| [preventUnapproved](https://github.com/jfrog/artifactory-user-plugins/blob/master/governance/preventUnapproved/README.md) | [preventUnapproved](https://github.com/jfrog/workers-sample/blob/main/samples/artifactory/BEFORE_DOWNLOAD/restrict-download-by-property-value/README.md) |
| [getP2Urls](https://github.com/jfrog/artifactory-user-plugins/blob/master/config/getAndSetP2Url/README.md) | X |
| [getPropertySetsList](https://github.com/jfrog/artifactory-user-plugins/blob/master/config/propertySetsConfig/README.md) | X |
| [getProxiesList](https://github.com/jfrog/artifactory-user-plugins/blob/master/config/proxiesConfig/README.md) | X |
| [getLayoutsList](https://github.com/jfrog/artifactory-user-plugins/blob/master/config/repoLayoutsConfig/README.md) | X |
| [webhook](https://github.com/yashprit-jfrog/artifactory-user-plugins/blob/master/webhook/README.md) | X |

> **Note**: For some user plugins, there is no direct worker sample available. You may need to adapt or refactor existing functionality using workers, depending on your specific use case.


## Contributing to Artifactory User Plugins
Although JFrog Workers is the preferred approach, we still accept contributions for Artifactory user plugins, particularly for those who cannot migrate to JFrog Workers at the moment. Contributions should follow the guidelines specified in the [CONTRIBUTING.md](CONTRIBUTING.md) file. 

To maintain a consistent and high-quality codebase, we encourage contributions to:
- **Fixing bugs or security issues**
- **Refactoring existing functionality for better compatibility with newer Artifactory versions**

## Migrating from User Plugins to JFrog Workers

If you are planning to migrate from Artifactory user plugins to JFrog Workers, follow the steps below:

1. **Evaluate**: Check the list of existing user plugins and identify the functionalities that need to be migrated.
2. **Explore Worker Samples**: Review existing [worker samples](https://github.com/jfrog/workers-sample) to understand how workers can replace your current plugin logic.
3. **Refactor and Deploy**: Migrate the logic to a JFrog Worker and test it in your development or staging environment.
4. **Monitor and Optimize**: Once the worker is running in production, monitor its performance and optimize as needed.

Migrating to JFrog Workers allows you to leverage the benefits of cloud-native architecture, including scalability, improved security, and better maintenance.

## Additional Resources

- **Artifactory REST API**: [API documentation](https://www.jfrog.com/confluence/display/JFROG/Artifactory+REST+API)
- **JFrog Community Support**: For troubleshooting, feature requests, or to engage with other developers using Artifactory and JFrog Workers, visit [JFrog Community](https://jfrog.com/community/).


## License & Copyright

Copyright © 2024, JFrog Ltd.

This project is licensed under the terms of the **Apache 2.0 License**, to learn more, refer to [LICENSE](https://github.com/jfrog/artifactory-user-plugins/blob/master/LICENSE).
