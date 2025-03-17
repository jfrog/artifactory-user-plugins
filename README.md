# Artifactory User Plugins

> **Note**: JFrog Workers is the recommended cloud-native solution for extending the JFrog Platform (including Artifactory). While user plugins are still supported, we recommend using JFrog Workers where possible for better scalability, security, and performance. [Learn more about JFrog Workers](https://jfrog.com/help/r/ybbUNZGwwAmzW2qGyL9Zdw/I4E5sOhWWpIHHfdV37__Iw).


## Upgrade Notice: Groovy 4 Compatibility

**Artifactory Version: 7.101 (released November 25, 2024 for cloud)**

Artifactory has been upgraded to Groovy 4 starting from version 7.101. This includes several important updates, most notably the promotion plugin (JFrog Supported), updated for compatibility with Groovy 4. However, this upgrade may break compatibility with your custom plugins developed for earlier Groovy versions (Groovy 3 or older). To learn the differences between Groovy 3 and 4, refer to [Release notes for Groovy 4.0
](https://groovy-lang.org/releasenotes/groovy-4.0.html)

<details>

<summary>To learn more, expand this drop-down</summary>

**JFrog Supported Plugins**

- **Promotion Plugin**
  
    The promotion plugin has been updated to work with Groovy 4. If you're using this plugin in your environment, redeploy it after upgrading to 7.101 or above.

- **Other Plugins**
  
  Other Plugins (Groovy 3 or older versions) work without redeploying after 7.101 or above upgrade.


> **Backward Compatibililty**:
> We do not support backward compatibility.

### Key Points to Consider

#### Groovy 4 Compatibility

Starting from Artifactory 7.101, Groovy 4 is the default version.

- **artifactory-user-plugins Branch Compatibility**

    - **master**: Artifactory 7.101 and above

        > **Note**:
        <br>Groovy 3 is no longer supported in the master branch of **artifactory-user-plugins**. Ensure that your plugins are compatible with Groovy 4.

    - **artifactory-groovy-3**: Artifactory 7.100 and below
  
- **JDK 17 Compatibility**
  
    Artifactory versions 7.43 and above have introduced JDK 17 support. This may cause older user plugins to break. Any JFrog-supported deprecated plugins are shown in [Deprecated Directory](http://github.com/jfrog/artifactory-user-plugins/tree/master/deprecated-plugins).

    > **Note**<br>
    >We recommend not using Deprecated Plugins as we don't test them anymore. If you are still using Deprecated Plugins, test them thoroughly to achieve the desired results.
  
#### Custom Plugins
If you have custom plugins developed before Groovy 4, you must update them to ensure compatibility with Groovy 4, as Artifactory version 7.101 (and above) will come bundled with Groovy 4. Plugins written for earlier versions of Groovy (Groovy 3 or older) may no longer work and will require changes and redeployed to work correctly from  Artifactory version 7.101 (and above).

> **Note:** Test your plugins thoroughly after upgrading Artifactory to 7.101 (and above).


#### Migrating to JFrog Workers
Consider migrating your plugins to JFrog Workers for a cloud-native and future-proof solution.
</details>

## Documentation References
- [Artifactory Documentation](https://jfrog.com/help/p/devops-home): Official documentation for Artifactory features, setup, and configuration.
- [User Plugin Documentation](https://jfrog.com/help/r/jfrog-integrations-documentation/user-plugins): Guide to writing and using custom Artifactory plugins.
- [JFrog Workers Documentation](https://jfrog.com/help/r/ybbUNZGwwAmzW2qGyL9Zdw/I4E5sOhWWpIHHfdV37__Iw): Learn more about JFrog Workers for extending Artifactory in a cloud-native way.

## Product feature alternative for plugins
Artifactory now has the inbuilt feature for some of the plugins.

| User Plugins | Product Feature |
|-------------|--------------------|
| [artifactCleanup](https://github.com/jfrog/artifactory-user-plugins/blob/master/cleanup/artifactCleanup/README.md) | Cleanup Policy (supported only for **Enterprise Plus** licenses), [learn more](https://jfrog.com/help/r/jfrog-platform-administration-documentation/cleanup-policies) | 
| [cleanDockerImages](https://github.com/jfrog/artifactory-user-plugins/blob/master/cleanup/cleanDockerImages/README.md) | Cleanup Policy (supported only for **Enterprise Plus** licenses), [learn more](https://jfrog.com/help/r/jfrog-platform-administration-documentation/cleanup-policies) | 
| [getPropertySetsList](https://github.com/jfrog/artifactory-user-plugins/blob/master/config/propertySetsConfig/README.md) | [UI Feature](https://jfrog.com/help/r/jfrog-artifactory-documentation/property-sets)|
| [getProxiesList](https://github.com/jfrog/artifactory-user-plugins/blob/master/config/proxiesConfig/README.md) | [UI Feature](https://jfrog.com/help/r/jfrog-platform-administration-documentation/manage-proxy-servers)|
| [getLayoutsList](https://github.com/jfrog/artifactory-user-plugins/blob/master/config/repoLayoutsConfig/README.md) | [UI Feature](https://jfrog.com/help/r/jfrog-artifactory-documentation/configure-repository-layouts) |
| [webhook](https://github.com/jfrog/artifactory-user-plugins/blob/master/webhook/README.md) | [Product Feature](https://jfrog.com/help/r/jfrog-platform-administration-documentation/webhooks) |
| [getP2Urls](https://github.com/jfrog/artifactory-user-plugins/blob/master/config/getAndSetP2Url/README.md) | [UI Feature](https://jfrog.com/help/r/jfrog-artifactory-documentation/additional-settings-for-docker-virtual-repositories) | 
| [deleteByPropertyValue](https://github.com/jfrog/artifactory-user-plugins/blob/master/cleanup/deleteByPropertyValue/README.md) | Cleanup Policy Roadmap |
| [deleteDeprecated](https://github.com/jfrog/artifactory-user-plugins/blob/master/cleanup/deleteDeprecated/README.md) | Cleanup Policy Roadmap |
| [oldBuildCleanup](https://github.com/jfrog/artifactory-user-plugins/blob/master/cleanup/oldBuildCleanup/README.md) | Cleanup Policy Roadmap |

## Worker alternative for Plugins
JFrog Workers are supported only for **Enterprise X** and **Plus** licenses. Workers offer an alternative to Artifactory user plugins, enabling you to extend the JFrog Platform using cloud-native, scalable, and secure solutions.

The following are the Worker alternatives for Artifactory plugins

| User Plugins | Worker Alternative |
|-------------|--------------------|
| [artifactCleanup](https://github.com/jfrog/artifactory-user-plugins/blob/master/cleanup/artifactCleanup/README.md) | [ArtifactCleanup Worker](https://github.com/jfrog/workers-sample/tree/main/samples/artifactory/GENERIC_EVENT/artifact-cleanup) |
| [cleanDockerImages](https://github.com/jfrog/artifactory-user-plugins/blob/master/cleanup/cleanDockerImages/README.md) | [cleanDockerImages Worker](https://github.com/jfrog/workers-sample/blob/main/samples/artifactory/GENERIC_EVENT/clean-docker-images/README.md) |
| [deleteEmptyDirs](https://github.com/jfrog/artifactory-user-plugins/blob/master/cleanup/deleteEmptyDirs/README.md) | [deleteEmptyDirsWorker](https://github.com/jfrog/workers-sample/blob/main/samples/artifactory/GENERIC_EVENT/delete-empty-dirs/README.md) |
| [repoQuota](https://github.com/jfrog/artifactory-user-plugins/blob/master/storage/repoQuota/README.md) | [repoQuota Worker](https://github.com/jfrog/workers-sample/blob/main/samples/artifactory/BEFORE_UPLOAD/repo-quota/README.md) |
| [restrictOverwrite](https://github.com/jfrog/artifactory-user-plugins/blob/master/storage/restrictOverwrite/README.md) | [restrictOverwrite Worker](https://github.com/jfrog/workers-sample/blob/main/samples/artifactory/BEFORE_UPLOAD/restrict-overwrite/README.md) |
| [repoStats](https://github.com/jfrog/artifactory-user-plugins/blob/master/stats/repoStats/README.md) | [repoStats Worker](https://github.com/jfrog/workers-sample/blob/main/samples/artifactory/GENERIC_EVENT/repoStats/README.md) |
| [preventUnapproved](https://github.com/jfrog/artifactory-user-plugins/blob/master/governance/preventUnapproved/README.md) | [preventUnapproved worker](https://github.com/jfrog/workers-sample/blob/main/samples/artifactory/BEFORE_DOWNLOAD/restrict-download-by-property-value/README.md) |
| [deleteByPropertyValue](https://github.com/jfrog/artifactory-user-plugins/blob/master/cleanup/deleteByPropertyValue/README.md) | [deleteByPropertyValue Worker](https://github.com/jfrog/workers-sample/blob/main/samples/artifactory/GENERIC_EVENT/delete-by-property-value/README.md) |
| [remoteBackup](https://github.com/jfrog/artifactory-user-plugins/blob/master/storage/remoteBackup/README.md) | [remoteBackup Worker](https://github.com/jfrog/workers-sample/blob/main/samples/artifactory/GENERIC_EVENT/remote-backup/README.md) |
| [backUpFolder](https://github.com/jfrog/artifactory-user-plugins/blob/master/backup/backupFolders/README.md) | X (worker limitation to support external modules) |
| [deleteDeprecated](https://github.com/jfrog/artifactory-user-plugins/blob/master/cleanup/deleteDeprecated/README.md) | [deleteDeprecated Worker](https://github.com/jfrog/workers-sample/blob/main/samples/artifactory/GENERIC_EVENT/delete-deprecated/README.md) |
| [mavenSnapshotCleanupWhenRelease](https://github.com/jfrog/artifactory-user-plugins/blob/master/cleanup/mavenSnapshotCleanupWhenRelease/README.md) | [mavenSnapshotCleanupWhenRelease Worker](https://github.com/jfrog/workers-sample/blob/main/samples/artifactory/eventCombinationWorkers/mavenSnapshotCleanupWhenRelease/README.md) |
| [oldBuildCleanup](https://github.com/jfrog/artifactory-user-plugins/blob/master/cleanup/oldBuildCleanup/README.md) | [oldBuildCleanup Worker](https://github.com/jfrog/workers-sample/blob/main/samples/artifactory/GENERIC_EVENT/oldBuildCleanup/README.md) |
| [checksums](https://github.com/jfrog/artifactory-user-plugins/blob/master/checksums/README.md) | X (worker limitation to support external modules) |

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
