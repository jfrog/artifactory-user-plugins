Artifactory Docker Image Promote Plugin
====================================


This user plugin promotes a docker image from one repository to another and updates docker build related to that image as well.


Installation
------------

To install, copy `imagePromote.groovy` into `$ARTIFACTORY_HOME/etc/plugins/`.

Execution
---------

``` shell
curl -XPOST -uadmin:password http://localhost:8081/artifactory/api/plugins/promotions/promoteDocker?params=targetRepository=docker-prod-local;status=Released;comment=promoting_docker_build
```

An `example.json` might be as follows:

``` yaml
{
    "sourceRepo":  "<sourceRepoKey>", //repoKey of source artifactory repository being used
    "dockerImage": "<pathOfImage>",   //path of docker image (i.e. <dockerRepo>/<dockerImage>)
    "sourceTag":   "<sourceTag>",     //tag name of source
    "destTag":     "<destTag>"        //destination tag name
}
```



*This plugin is currently being tested for Artifactory 5.x releases.*
