Artifactory Archive Old Artifacts User Plugin
=============================================

This plugin is used to archive artifacts from a given source repository in
Artifactory to a given destination repository. The artifacts are chosen based on
a mixture of available parameters.

Installation
------------

To install this plugin:

1. Edit the `${ARTIFACTORY_HOME}/etc/logback.xml` to add:

    ```
    <logger name="archive_old_artifacts">
        <level value="warn"/>
    </logger>
    ```

2. Place this script under the master Artifactory server
   `${ARTIFACTORY_HOME}/etc/plugins`.
3. Verify in the ${ARTIFACTORY_HOME}/logs/artifactory.log that the plugin loaded
   correctly.

Features
--------

- Re-deploys artifacts that are to be archived with a 1-byte file, to save disk
  space.
- Archived artifacts are moved to an archive repository, to be separate from
  non-archived artifacts.
- Archived artifacts retain all properties that were set, and are also tagged
  with the archival timestamp.

Input Parameters
----------------

- `filePattern` - the file pattern to match against in the source repository
- `srcRepo` - the source repository to scan for artifacts to be archived
- `archiveRepo` - the repository where matching artifacts are archived to
- `archiveProperty` - the name of the property to use when tagging the archived
  artifact with the archive timestamp

### Available 'time period' archive policies: ###

- `lastModified` - the last time the artifact was modified
- `lastUpdated` - the last time the artifact was updated
- `created` - the creation date of the artifact
- `lastDownloaded` - the last time the artifact was downloaded
- `age` - the age of the artifact

NOTE: the time period archive policies are all specified in number of days

### Available 'property' archive policies: ###

- `includePropertySet` - the artifact will be archived if it possesses all of
  the passed in properties
- `excludePropertySet` - the artifact will not be archived if it possesses all
  of the passed in properties

NOTE: property set format &rArr;
`prop[:value1[;prop2[:value2]......[;propN[:valueN]]])`

A property key must be provided, but a corresponding value is not necessary.
If a property is set without a value, then a check is made for just the key.

### Available artifact keep policy: ###

- `numKeepArtifacts` - the number of artifacts to keep per directory

NOTE: This allows one to keep X number of artifacts (based on natural directory
sort per directory). So, if your artifacts are laid out in a flat directory
structure, you can keep the last X artifacts in each directory with this
setting.

One can set any number of 'time period' archive policies as well as any number
of include and exclude attribute sets. It is up to the caller to decide how best
to archive artifacts. If no archive policy parameters are sent in, the plugin
aborts in order to not allow default deleting of every artifact.

Permissions
------------

In order to call the plugin execute REST API, you must call it with an **admin**
user with HTTP authentication.

Sample REST Calls
-----------------

- Archive any artifact over 30 days old:

  `curl -X POST -v -u admin:password "http://localhost:8080/artifactory/api/plugins/execute/archive\_old_artifacts?params=ageDays=30"`
- Archive any artifact that is 30 days old and has the following properties set:


For Artifactory 4.x:
  `curl -X POST -v -u admin:password "http://localhost:8080/artifactory/api/plugins/execute/archive\_old_artifacts?params=ageDays=30|includedPropertySet=deleteme:true;junk:true"` 
  
  
- Archive any artifact that has not been downloaded in 60 days, excluding those
  with a certain property set:


For Artifactory 4.x:
  `curl -X POST -v -u admin:password "http://localhost:8080/artifactory/api/plugins/execute/archive\_old_artifacts?params=lastDownloadedDays=60|excludePropertySet=keeper:true"` 
  
For Artifactory 5.x:
  `curl -X POST -v -u admin:password "http://localhost:8080/artifactory/api/plugins/execute/archive\_old_artifacts?params=lastDownloadedDays=60;excludePropertySet=keeper:true"`  
- Archive only `*.tgz` files that are 30 days old and have not been downloaded
  in 15 days:


For Artifactory 4.x:
  `curl -X POST -v -u admin:password "http://localhost:8080/artifactory/api/plugins/execute/archive\_old_artifacts?params=filePattern=*.tgz|ageDays=30|lastDownloadedDays=15"` 
  
  
For Artifactory 5.x: 
`curl -X POST -v -u admin:password "http://localhost:8080/artifactory/api/plugins/execute/archive\_old_artifacts?params=filePattern=*.tgz;ageDays=30;lastDownloadedDays=15"`  
- Archive any `*.tgz` artifact that is 30 days old and is tagged with
  `artifact.delete`:


For Artifactory 4.x:
  `curl -X POST -v -u <admin_user>:<admin_password> "http://localhost:8080/artifactory/api/plugins/execute/archive_old_artifacts?params=filePattern=*.tgz|ageDays=30|includePropertySet=artifact.delete"` 
  
For Artifactory 5.x:
`curl -X POST -v -u <admin_user>:<admin_password> "http://localhost:8080/artifactory/api/plugins/execute/archive_old_artifacts?params=filePattern=*.tgz;ageDays=30;includePropertySet=artifact.delete"`  
- Archive any `*.tgz` artifact that is 15 days old and is tagged with
  `artifact.delete=true`:


For Artifactory 4.x:
  `curl -X POST -v -u <admin_user>:<admin_password> "http://localhost:8080/artifactory/api/plugins/execute/archive_old_artifacts?params=filePattern=*.tgz|ageDays=15|includePropertySet=artifact.delete:true"` 
  
 
For Artifactory 5.x:
`curl -X POST -v -u <admin_user>:<admin_password> "http://localhost:8080/artifactory/api/plugins/execute/archive_old_artifacts?params=filePattern=*.tgz;ageDays=15;includePropertySet=artifact.delete:true"`  

Archive Process
---------------

The 'archive' process performs the following:

- Grabs all of the currently set properties on the artifact
- Does a deploy over top of the artifact with a 1-byte size file, to conserve
  space
- Adds all of the previously held attributes to the newly deployed 1-byte size
  artifact
- Moves the artifact from the source repository to the destination repository
  specified
- Adds a property containing the archive timestamp to the artifact
