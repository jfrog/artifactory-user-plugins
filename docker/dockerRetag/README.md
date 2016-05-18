This user plugin makes a copy of an existing tag in the current repository with a new tag name.  If the new tag name already exists, it is DELETED.

Input json:

{
	"sourceRepo"	: "<sourceRepoKey>",	//repoKey of source artifactory repository being used
	"dockerImage"	: "<pathOfImage>", 	//path of docker image (i.e. <dockerRepo>/<dockerImage>)
	"sourceTag"	: "<sourceTag>", 	//tag name of source
	"destTag"	: "<destTag>"		//destination tag name
}

