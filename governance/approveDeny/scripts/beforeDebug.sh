#!/bin/bash

docker cp approveDeny.json  rt1:/opt/jfrog/artifactory/var/etc/artifactory/plugins/approveDeny.json
docker cp approveDeny.groovy  rt1:/opt/jfrog/artifactory/var/etc/artifactory/plugins/approveDeny.groovy


docker cp lib/http-builder-0.7.2.jar  rt1:/opt/jfrog/artifactory/var/etc/artifactory/plugins/lib/http-builder-0.7.2.jar
docker cp lib/json-lib-2.4-jdk13.jar  rt1:/opt/jfrog/artifactory/var/etc/artifactory/plugins/lib/json-lib-2.4-jdk13.jar
docker cp lib/xml-resolver-1.2.jar  rt1:/opt/jfrog/artifactory/var/etc/artifactory/plugins/lib/xml-resolver-1.2.jar
#docker cp lib/ezmorph-1.0.6.jar  rt1:/opt/jfrog/artifactory/var/etc/artifactory/plugins/lib/ezmorph-1.0.6.jar

curl -u admin:password -XPOST -H'Content-Type: application/json' http://localhost:8082/artifactory/api/plugins/reload
