# rlBlock - Artifactory User Plugin

[ReversingLabs](https://www.reversinglabs.com/) provides a JFrog Artifactory plugin called `rlBlock` for users of the [rl-scan-artifactory](https://pypi.org/project/rl-scan-artifactory/) integration.

The integration uses the ReversingLabs Spectra Assure CLI (`rl-secure`) or the Spectra Assure Portal to scan software packages in Artifactory repositories.
When a package is scanned, it receives the `pass` or `fail` status as part of the scan results.

You can then use the `rlBlock` plugin to automatically block download attempts for software packages with the `fail` status.

**Important:** the `rlBlock` plugin is intended to be used in combination with the [rl-scan-artifactory](https://pypi.org/project/rl-scan-artifactory/) integration. It does not support any other use-cases.


## What is Spectra Assure?

The [Spectra Assure platform](https://www.reversinglabs.com/products/software-supply-chain-security)
is a set of ReversingLabs products primarily designed for software assurance and software supply chain security use-cases.

It helps users protect their software supply chains by analyzing compiled software packages,
their components and third-party dependencies to detect exposures, reduce vulnerabilities, and eliminate threats before reaching production.

Users can choose to work with Spectra Assure as an on-premises [CLI tool](https://docs.secure.software/cli/),
a ReversingLabs-hosted [SaaS solution called Portal](https://docs.secure.software/portal/),
or use Spectra Assure [Docker images and integrations](https://docs.secure.software/integrations) in their CI/CD pipelines.


## How this plugin works

When you use the [rl-scan-artifactory](https://pypi.org/project/rl-scan-artifactory/) integration to scan artifacts with Spectra Assure, specific **properties** will be set on relevant scanned items.

The `rlBlock` plugin will prevent artifacts that have failed the Spectra Assure scan from being downloaded, unless explicitly allowed by ip4 address or repository name.

The 403 response from the `rlBlock` plugin will include the link to the analysis report. 
In the report, users can find out more about the reasons why the block was enforced.

More specifically, the plugin will block download attempts if the property `RL.scan-status` has a value of 'fail'.

Optionally, the plugin supports allowlisting:

- by `name`, so that the download will always succeed for the specified repositories
- by `ip4-addresses`, so that the download will always succeed if it is requested from one of the specified IP addresses

The plugin can be switched off in the `rlBlock.properties` configuration file.


## Prerequisites

To successfully use the `rlBlock` Artifactory plugin, you should:

- Get familiar with [JFrog Artifactory User Plugins](https://jfrog.com/help/r/jfrog-integrations-documentation/user-plugins) to understand how to deploy them

- Set up the [rl-scan-artifactory](https://pypi.org/project/rl-scan-artifactory/) integration to be able to scan artifacts with ReversingLabs Spectra Assure. Note that the integration requires you to have a license for at least one of the Spectra Assure products (CLI or Portal)


## Installation

To install the `rlBlock` plugin:

1. Access the plugin source files on GitHub.

2. Copy the `rlBlock.groovy` script and the `rlBlock.properties` file into the `/opt/jfrog/artifactory/var/etc/artifactory/plugins/` directory.

3. Configure the plugin settings in the `rlBlock.properties` file.

Optionally: configure [custom logging](#custom-logging).


## Plugin configuration

To activate the plugin, access the `rlBlock.properties` file in the plugin directory and modify the configuration:

```
/opt/jfrog/artifactory/var/etc/artifactory/plugins/rlBlock.properties
```

**After saving changes to the file, restart Artifactory to activate the plugin and apply your configuration changes.**


### Blocking downloads

**Required for the plugin to function**

In the `rlBlock.properties` file, set the `block_downloads_failed` field to `'true'`.

```
block_downloads_failed = 'true'
```


### Allowlisting

**Optional**

The `rlBlock.properties` file supports allowlisting for repositories and IP addresses.

To ensure that specific repositories will never block any download request, you can populate the field `never_block_repo_list` like in the following example:

```
never_block_repo_list = [ 'repoName1' , 'repoName2']
```

To ensure that download requests from specific IP addresses will never be blocked, you can populate the field `never_block_ip_list` like in the following example:

```
never_block_ip_list = ['1.2.3.4' , '5.6.7.8']
```

This option is particularly suited for allowing all downloads from the host that runs the `rl-scan-artifactory` integration.


### Adding contact information

**Optional**

By default, the 403 response produced by the `rlBlock` plugin includes a link to the analysis report to give users more context on why blocking was enforced for a specific artifact.

You can extend the default response with information on how to contact an administrator:

```
admin_name = 'Your Friendly Administrator'
admin_email = 'your.friendly@administrator.noreply'
```


## Custom logging

Optionally, logging can be configured to write all activities of the `rlBlock` plugin to the default logging directory in Artifactory:

- `/opt/jfrog/artifactory/var/log/rlBlock.log`

To do this, modify the logging configuration file:

- `/opt/jfrog/artifactory/var/etc/artifactory/logback.xml`

At the end of the file just before the closing XML tag `</configuration>`, add the following:

```xml
  <!--Plugin: rlBlock appender -->
  <appender name="rlBlock" class="ch.qos.logback.core.rolling.RollingFileAppender">
    <File>${log.dir}/rlBlock.log</File>
    <encoder>
      <pattern>%date{yyyy-MM-dd'T'HH:mm:ss.SSS, UTC}Z [%p] - %m%n</pattern>
    </encoder>
    <rollingPolicy class="ch.qos.logback.core.rolling.FixedWindowRollingPolicy">
      <FileNamePattern>${log.dir.archived}/rlBlock.%i.log.gz</FileNamePattern>
      <maxIndex>13</maxIndex>
    </rollingPolicy>
    <triggeringPolicy class="ch.qos.logback.core.rolling.SizeBasedTriggeringPolicy">
      <MaxFileSize>25MB</MaxFileSize>
    </triggeringPolicy>
  </appender>
  <!--Plugin: rlBlock logger -->
  <logger name="rlBlock" level="info" additivity="false">
    <level value="info" />
    <appender-ref ref="rlBlock" />
  </logger>
```

You can modify the `maxIndex` and `MaxFileSize` values to suit your requirements.

**Restart Artifactory to apply your logging configuration changes.**
