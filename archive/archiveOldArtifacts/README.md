Artifactory Archive Old Artifacts User Plugin
=============================================

Archiving old artifacts, by means of deleting them completely, or moving them to a local directory.

This plugin is used to archive artifacts from a given source repository in Artifactory to a given destination repository.
The artifacts are selected based on a mixture of available parameters.

The archive process is designed to preserve the name, path, and properties of an artifact,
but save disk space by deleting the file contents.
The artifact contents will be replaced with a one-byte file,
and this 'stub' file will then be moved to the archive repository.
This plugin is to be used for build artifacts that are no longer needed,
when it's still useful to keep the artifact info around for auditing or history purposes.

**Note that this plugin will delete your artifacts by default**.  
If you want to backup the artifacts before deleting them,
check the `copyArtifactToDisk` param.

## Features

- Re-deploys artifacts that are to be archived with a 1-byte file,
  to save disk space
- Archived artifacts can be copied to an archive disk,
  before removal from Artifactory
- Artifacts are selected based on a mixture of available parameters
- Archived artifacts are moved to an archive repository,
  to be separate from non-archived artifacts
- Archived artifacts retain all properties that were set,
  and are also tagged with the archival timestamp

## Archive Process

The 'archive' process performs the following:

 - Grabs all of the currently set properties on the artifact
 - If `copyArtifactToDisk` is `true`; artifact is downloaded to the local disk path
   as configured with the environment variable `ARTIFACTORY_DATA_ARCHIVE`
 - Does a deploy over the artifact with a small size file (to conserve space),
   explaining the file is archived, mentioning the archived location if `copyArtifactToDisk` is `true`
 - Adds all of the previously held attributes to the newly deployed small size artifact
 - Moves the artifact from the source repository to the destination repository specified
 - Adds a property containing the archive timestamp to the artifact

## Provides

### POST \<api url>/plugins/execute/archive_old_artifacts

_Requires **admin** permissions for execution_

```
archive_old_artifacts(
        description: 'Archive old artifacts',
        version: '1.0',
        httpMethod: 'POST',
        params: Globals.DEFAULT_PARAMS,
) { params ->
```

```
static final Map<String, Object> DEFAULT_PARAMS = [
        pathPattern       : '**',
        filePattern       : '*',
        srcRepo           : '',
        archiveRepo       : '',
        lastModifiedDays  : 0,
        lastUpdatedDays   : 0,
        createdDays       : 0,
        lastDownloadedDays: 0,
        ageDays           : 0,
        excludePropertySet: '',
        includePropertySet: '',
        archiveProperty   : 'archived.timestamp',
        numKeepArtifacts  : 0,
        copyArtifactToDisk: false,
]
```

## Notes

* The plugin is only executable by admin users
* **If the plugin cannot be loaded**,
  Artifactory will not fail, at all!
  It will just start without the plugin loaded.
  Monitor `GET <api url>/api/plugins` to see if the plugin is loaded
* The default log level for user plugins is "warn".
  To change a plugin log level,
  add the following to `${ARTIFACTORY_HOME}/etc/logback.xml`:
  ```
  <logger name="archiveOldArtifacts">
    <level value="info"/>
  </logger>
  ```
  The logger name is the name of the plugin file without the ".groovy" extension
  (in the example above the plugin file name is `archiveOldArtifacts.groovy`).
  The logging levels can be either `error`, `warn`, `info`, `debug`, or `trace`

### Performance

This plugin has minimal impact on your Artifactory instance.
It has been tested and running on a heavy traffic, high load, single Artifactory instance,
while archiving about 11 TB to disk, without noticeable CPU or memory increase.

After the initial archive action of 11 TB,
it's happily running multiple scheduled archive actions during daily loads.

## Install

1. Put `archiveOldArtifacts.groovy` into
   `/var/opt/jfrog/artifactory/etc/plugins/archiveOldArtifacts.groovy`
   of your Artifactory instance
2. Restart your Artifactory instance,
   or reload the plugins through the Artifactory API through `POST /api/plugins/reload`:
   https://www.jfrog.com/confluence/display/RTF/Artifactory+REST+API#ArtifactoryRESTAPI-ReloadPlugins
3. \#profit

### Support for `copyArtifactToDisk` before removal

To support copying artifacts to disk before they get removed from Artifactory,
you additionally need to:

1. Configure the `ARTIFACTORY_DATA_ARCHIVE` environment variable to the full path of where archived artifacts should be copied to.
   This environment variable needs to be available to Artifactory on start-up!
   Otherwise it will not be taken into account.
2. Restart your Artifactory instance
3. Set the `copyArtifactToDisk` param to `true` for those artifacts you want keep as archive

Tip: Mount a backup storage to the path of the `ARTIFACTORY_DATA_ARCHIVE`

Artifacts copied to disk, are stored in a flat-directory structure that resembles their original location in Artifactory.  
eg. If an artifact had an original path in Artifactory of `com/company/app/version/version.jar` in repo `deploy-local`,
it's stored on disk in `${ARTIFACTORY_DATA_ARCHIVE}/deploy-local/com/company/app/version/version.jar`

## Usage

### Input Parameters

- `pathPattern` - the glob that the path of the artifact should match
- `filePattern` - the glob that the name of the artifact filename should match
- `srcRepo` - the source repository to scan for artifacts to be archived
- `archiveRepo` - the repository where matching artifacts are archived to
- `archiveProperty` - the name of the property to use when tagging the archived artifact with the archive timestamp

What is a glob?
See https://docs.oracle.com/javase/tutorial/essential/io/fileOps.html#glob

#### Available 'time period' archive policies

- `lastModified` - the last time the artifact was modified
- `lastUpdated` - the last time the artifact was updated
- `created` - the creation date of the artifact
- `lastDownloaded` - the last time the artifact was downloaded
- `age` - the age of the artifact

NOTE: the time period archive policies are all specified in number of days

#### Available 'property' archive policies

- `includePropertySet` - the artifact will be archived if it possesses all of
  the passed in properties
- `excludePropertySet` - the artifact will not be archived if it possesses all
  of the passed in properties

NOTE: property set format &rArr;
`prop[:value1[;prop2[:value2]......[;propN[:valueN]]])`

A property key must be provided, but a corresponding value is not necessary.
If a property is set without a value, then a check is made for just the key.

#### Available artifact keep policy

- `numKeepArtifacts` - the number of artifacts to keep per directory

NOTE: This allows one to keep X number of artifacts (based on natural directory
sort per directory). So, if your artifacts are laid out in a flat directory
structure, you can keep the last X artifacts in each directory with this
setting.

One can set any number of 'time period' archive policies as well as any number
of include and exclude attribute sets. It is up to the caller to decide how best
to archive artifacts. If no archive policy parameters are sent in, the plugin
aborts in order to not allow default deleting of every artifact.

#### Available archive actions

- `copyArtifactToDisk` - true/false (default false); if true, archived artifacts are downloaded to the local disk path
  as configured with the environment variable `ARTIFACTORY_DATA_ARCHIVE`.
  If false, archived artifacts are deleted!

### Sample REST Calls

- Archive any artifact over 30 days old:
  ```
  curl -X POST -v -u <admin_user>:<admin_password> "http://localhost:8088/artifactory/api/plugins/execute/archive_old_artifacts?params=ageDays=30"
  ```
- Archive any artifact that is 30 days old and has the following properties set:
  ```
  curl -X POST -v -u <admin_user>:<admin_password> "http://localhost:8088/artifactory/api/plugins/execute/archive_old_artifacts?params=ageDays=30;includePropertySet=deleteme:true;junk:true"
  ```
- Archive any artifact that has not been downloaded in 60 days, excluding those with a certain property set:
  ```
  curl -X POST -v -u <admin_user>:<admin_password> "http://localhost:8088/artifactory/api/plugins/execute/archive_old_artifacts?params=lastDownloadedDays=60;excludePropertySet=keeper:true"
  ```
- Archive only *.tgz files that are 30 days old and have not been downloaded in 15 days:
  ```
  curl -X POST -v -u <admin_user>:<admin_password> "http://localhost:8088/artifactory/api/plugins/execute/archive_old_artifacts?params=filePattern=*.tgz;ageDays=30;lastDownloadedDays=15"
  ```
- Archive any *.tgz artifact that is 30 days old and is tagged with artifact.delete:
  ```
  curl -X POST -v -u <admin_user>:<admin_password> "http://localhost:8088/artifactory/api/plugins/execute/archive_old_artifacts?params=filePattern=*.tgz;ageDays=30;includePropertySet=artifact.delete"
  ```
- Archive any *.tgz artifact that is 15 days old and is tagged with artifact.delete=true:
  ```
  curl -X POST -v -u <admin_user>:<admin_password> "http://localhost:8088/artifactory/api/plugins/execute/archive_old_artifacts?params=filePattern=*.tgz;ageDays=15;includePropertySet=artifact.delete:true"
  ```

Still using Artifactory 4.x? The replace the `;` characters in the params with `|`.
  
### Sample scheduled cleanups

The plugin contains examples on how to schedule cleanups.
Check the plugin source and search for `jobs {`

## Develop on the plugin

Artifactory plugin development is a mess,
you need an actual Artifactory instance to be able to do anything.
Besides that,
the Plugin API (papi) is really limited.
For anything useful,
you are dependent on the Artifactory internals,
sadly.

We tried to make it as simple as possible.

### Requirementes

* `docker` installed locally
* Valid Artifactory license file available in `artifactory.lic` file in the root of the project
* At least 2 GB of free disk space for the tests to run,
  as the some of the tests test with actual files of 5 MB, 100 MB, and 1 GB,
  to test the performance of the `copyArtifactToDisk` param

### Usage

* You can load this gradle project in your favorite IDE,
  and it should just work, code wise, including running tests
  (if you have a local Artifactory instance running)
* Testing the plugin:
  * Run `./startArtifactory.sh` to spin-up a test Artifactory locally
    * The plugin is automatically loaded into this Artifactory instance
    * The Artifactory instance is available on http://localhost:8088/artifactory
    * Admin user and password is: admin/password
    * Changes to the plugin code are *not* automatically applied to the running Artifactory instance!
      You need to kill the container and run `./startArtifactory.sh` script again
  * Run the tests in the project through `./gradlew check`,
    or through your favorite IDE

### Resources

* Artifactory source,
to figure out the internals:
  https://api.bintray.com/content/jfrog/artifactory/jfrog-artifactory-oss-$latest-sources.tar.gz;bt_package=jfrog-artifactory-oss-zip  
  **NOTE:** The Artifactory source is not 100%,
  they remove parts of the code by replacing it with default or stub code before publishing.
  That makes it hard to completely understand how Artifactory works.
* Artifactory User Plugin documentation:
  https://www.jfrog.com/confluence/display/RTF/User+Plugins
* Artifactory REST API docs:
  https://www.jfrog.com/confluence/display/RTF/Artifactory+REST+API
* Artifactory Plugin API (papi):
  * source: https://search.maven.org/search?q=a:artifactory-papi
  * docs: http://repo.jfrog.org/artifactory/oss-releases-local/org/artifactory/artifactory-papi/%5BRELEASE%5D/artifactory-papi-%5BRELEASE%5D-javadoc.jar!/index.html
* Repository of open-source, Community-driven, sample Artifactory plugins:
  https://github.com/JFrog/artifactory-user-plugins
