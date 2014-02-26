Artifactory Archive Old Artifacts User Plugin
=============================================

This plugin is used to archive artifacts from a given source repository in Artifactory to a given destination repository. The artifacts are chosen based on a mixture of available parameters.


Installation
------------

To install this plugin:

1. Edit the ${ARTIFACTORY_HOME}/etc/logback.xml to add:

    ```
    <logger name="archive_old_artifacts">
        <level value="warn"/>
    </logger>
    ```

2. Place this script under the master Artifactory server ${ARTIFACTORY_HOME}/etc/plugins.
3. Verify in the ${ARTIFACTORY_HOME}/logs/artifactory.log that the plugin loaded correctly.

Features
--------

- Re-deploys artifacts that are to be archived with a 1-byte file, to save disk space
- Archived artifacts are moved to an archive repository, to be separate from non-archived artifacts
- Archived artifacts retain all properties that were set, and are also tagged with the archival timestamp

Input Parameters
----------------

- filePattern - the file pattern to match against in the source repository
- srcRepo - the source repository to scan for artifacts to be archived
- archiveRepo - the repository where matching artifacts are archived to
- archiveProperty - the name of the property to use when tagging the archived artifact with the archive timestamp

### Available 'time period' archive policies:

- lastModified - the last time the artifact was modified 
- lastUpdated - the last time the artifact was updated
- created - the creation date of the artifact
- lastDownloaded - the last time the artifact was downloaded
- age - the age of the artifact

NOTE: the time period archive policies are all specified in number of days
  
### Available 'property' archive policies:

- includePropertySet - the artifact will be archived if it possesses all of the passed in properties
- excludePropertySet - the artifact will not be archived if it possesses all of the passed in properties

NOTE: property set format ==> prop1:value1;prop2:value2;......propN:valueN
  
One can set any number of 'time period' archive policies as well as any number of include and exclude attribute sets. It is up to the caller to decide how best to archive artifacts. If no archive policy parameters are sent in, the plugin aborts in order to not allow default deleting of every artifact. 

Permissions
------------

In order to call the plugin execute REST API, you must call it with an **admin** user with HTTP authentication.

Sample REST Calls
-----------------

- Archive any artifact over 30 days old:

    curl -X POST -v -u admin:password "http://localhost:8080/artifactory/api/plugins/execute/archive\_old_artifacts?params=ageDays=30"
- Archive any artifact that is 30 days old and has the following properties set:

    curl -X POST -v -u admin:password "http://localhost:8080/artifactory/api/plugins/execute/archive\_old_artifacts?params=ageDays=30|includedPropertySet=deleteme:true;junk:true"
- Archive any artifact that has not been downloaded in 60 days, excluding those with a certain property set:

    curl -X POST -v -u admin:password "http://localhost:8080/artifactory/api/plugins/execute/archive\_old_artifacts?params=lastDownloadedDays=60|excludedPropertySet=keeper:true"
- Archive only *.tgz files that are 30 days old and have not been downloaded in 15 days:

    curl -X POST -v -u admin:password "http://localhost:8080/artifactory/api/plugins/execute/archive\_old_artifacts?params=filePattern=*.tgz|ageDays=30|lastDownloadedDays=15"

Archive Process
---------------

The 'archive' process performs the following:

- Grabs all of the currently set properties on the artifact
- Does a deploy over top of the artifact with a 1-byte size file, to conserve space
- Adds all of the previously held attributes to the newly deployed 1-byte size artifact
- Moves the artifact from the source repository to the destination repository specified
- Adds a property containing the archive timestamp to the artifact
