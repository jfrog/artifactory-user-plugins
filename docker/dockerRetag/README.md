NOTE:  This plugin is deprecated as of Artifactory v4.10.  Instead you should use the built-in [docker promote rest API](https://www.jfrog.com/confluence/display/RTF/Artifactory+REST+API#ArtifactoryRESTAPI-PromoteDockerImage) to re-tag images.

This user plugin makes a copy of an existing tag in the current repository with a new tag name.  If the new tag name already exists, it is DELETED.

To Install:

	copy dockerRetag.groovy into $ARTIFACTORY_HOME/etc/plugins/

To Execute:

	curl -uadmin:password -X POST http://localhost:8081/artifactory/api/plugins/execute/dockerRetag -T example.json


Input json:

	{
		"sourceRepo"	: "<sourceRepoKey>",	//repoKey of source artifactory repository being used
		"dockerImage"	: "<pathOfImage>", 	//path of docker image (i.e. <dockerRepo>/<dockerImage>)
		"sourceTag"	: "<sourceTag>", 	//tag name of source
		"destTag"	: "<destTag>"		//destination tag name
	}


