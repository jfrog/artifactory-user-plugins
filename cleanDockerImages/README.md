Artifactory Clean Docker Images User Plugin
===========================================

*This plugin is currently being tested for Artifactory 5.x releases.*
*Please follow the standard steps to deploy a Artifactory plugin. Please refer to Artifactory User Guide for User Plugin to deploy a plugin*

Summary:


This is a Artifactry REST executable user plugin. This plugin can be used to clean docker images based on the cleanup policies. Currently, this plugin supports below variables as cleanup policies.

1) maxDays: The maximum number of days a docker image can live in a Artifactory repository. Any expired docker images (today minus docker image create date in the repositoy) will be deleted for cleanup.
2) maxCount: The maximum count of versions of a image that should should live in a Artifactory. For example, if there are 10 versions of a docker image and if maxCount is set to 6, delete the first 4 versions of the image. The versions are based on the create date of the Artifactory image


Please note that the above variables are set as docker labels in Dockerfile when the docker images are created. Please refer to Step 2 of the Settings section below.


Settings (before the plugin is deployed):

1) List the names of the repositories that you need to scan for policies in the array in the plugin code(String[] dockerRepos = ["docker-local-repo", "app1-docker-repo"]). Please note to include docker type repositories only. This plugin does not work of non-docker repositories

2) Set labels for docker images. Example lines from Dockerfile to set the label are below:
	LABEL com.jfrog.artifactory.retention.maxCount="10" \
	com.jfrog.artifactory.retention.maxDays="7"

	Currently, the names of the labels have to be the above. If you change the names of the maxCount and maxDays labels, the appropriate property names have to be changes in the plugin code. 
	
	The docker labels will set as properties inside Artifactory once the images is deployed into a docker repository. The best practice is not to touch the properties once the image is created. This plugin uses these properties to perform cleanup. The below


Plugin's Key Steps:

1) Scans the docker specific repositories mentioned in the step 1 of the Settings.
2) Identifies the images that are expired based on the maxDays logic that is mentioned above. And deletes the images immediately
3) Identifies and gathers the images information that are expired based on the maxCount logic explained above.
4) Deletes the images based on the gathered images information.

How to run the Plugin?

This is a REST executable user plugin. This plugin can be called using the below sample URL:

http://<artifactory domain>:<artifactory port>/artifactory/api/plugins/execute/cleanDockerImages

