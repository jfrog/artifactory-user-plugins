Artifactory Checkmarx SCA User Plugin
=============================================

The Checkmarx SCA plugin for JFrog Artifactory runs a Checkmarx SCA scan on each of your Jfrog artifacts, and uses the scan results to enrich the properties shown in the JFrog Artifactory UI. This integrates scanning of artifacts into your DevOps workflow, providing easy visibility into possible risks that could make your applications vulnerable.

You can set a risk threshold so that artifacts with risks of a specified severity level will automatically be blocked from download.

When you install the plugin, Checkmarx scans all artifacts currently in your Artifactory. In addition, each time that an artifact is downloaded the plugin runs a Checkmarx SCA scan on that item. In order to avoid redundant scanning of the same artifact, a cache mechanism is used to reuse scan results for a fixed period of time (default: 6 hr).

Installation
------------

To install this plugin:

1. Place the groovy file and properties file into 
   `${ARTIFACTORY_HOME}/var/etc/artifactory/plugins`.   
2. Place the jar file in lib folder into 
	`${ARTIFACTORY_HOME}/var/etc/artifactory/plugins/lib`
3. If your JFrog instance is not configured to reload plugins automatically (this is the default configuration), then you will need to manually reload the plugins (e.g., POST http://<JFrogURL>/artifactory/api/plugins/reload).
 

Event Logs
--------

By default the plugin logs are written to the general system logs file. By default the log level is set as INFO.
You can configure the logs to be sent to a dedicated Checkmarx log file. You can also change the log level.

To create a dedicated log file:
1. Open the ${ARTIFACTORY_HOME}/var/etc/artifactory/logback.xml) file.
2. Append the following snippet to the file in order to create a dedicated log file.
`<appender name="CXSCA" class="ch.qos.logback.core.rolling.RollingFileAppender">
  <File>${log.dir}/cxsca.log</File>

  <rollingPolicy class="org.jfrog.common.logging.logback.rolling.FixedWindowWithDateRollingPolicy">
    <FileNamePattern>${log.dir.archived}/cxsca.%i.log.gz</FileNamePattern>
  </rollingPolicy>

  <triggeringPolicy class="org.jfrog.common.logging.logback.triggering.SizeAndIntervalTriggeringPolicy">
    <MaxFileSize>25MB</MaxFileSize>
  </triggeringPolicy>

  <encoder class="ch.qos.logback.core.encoder.LayoutWrappingEncoder">
    <layout class="org.jfrog.common.logging.logback.layout.BackTracePatternLayout">   
      <pattern>%date{yyyy-MM-dd'T'HH:mm:ss.SSS, UTC}Z [jfrt ] [%-5p] [%-16X{uber-trace-id}] [%-30.30(%c{3}:%L)] [%-20.20thread] - %m%n</pattern>
    </layout>
  </encoder>
</appender>`
3. If you would also like to change the log level, add the following code to the file:
`<logger name="com.checkmarx.sca.cxsca-security-plugin" additivity="false" level="DEBUG">
  <appender-ref ref="CXSCA"/>
</logger>`
	  
### Execution ###
By default, when an artifact is reused within 6 hours the scan data from the cache is reused instead of triggering a new scan. If you would like to adjust the time span, use the following procedure.
1. Open the cxsca-security-plugin.properties file.
2. In the line `sca.data.expiration-time=21600`, replace `21600` with the desired time span for using the cache (in seconds).
The minimum acceptable value for cache expiration is 1800 (30 min.).

You can set a risk threshold so that artifacts with risks of the specified severity level (or above) will be blocked from download. To set a threshold, use the following procedure.
1. Open the cxsca-security-plugin.properties file.
2. In the line `sca.security.risk.threshold=none`, replace `none` with the desired threshold, options are `low`, `medium` or `high`.

Each artifact that has one or more risks of the specified severity level or above will be blocked from download. You can override the threshold for specific artifacts when needed.
To override the threshold:
1. Open the properties tab for the desired artifact.
2. Add a property `CxSCA.IgnoreRiskThreshold` and set the value to `true`.

You can specify a list of allowed licenses so that artifacts that do not have an allowed license are blocked from download. To set the list of allowed licenses, use the following procedure.
1. Open the `cxsca-security-plugin.properties` file.
2. Add the property `sca.licenses.allowed`, and add a comma separated list of allowed licenses. For example:
`sca.licenses.allowed=MIT,APACHE`

If you have set a limitation to block download of packages with licenses that aren't included in your "allowed" list, you can override this limitation for specific artifacts.
To override the limitation:
1. Open the properties tab for the desired artifact.
2. Add a property `CxSCA.IgnoreLicenses` and set the value to `true`.
