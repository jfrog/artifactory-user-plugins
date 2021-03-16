#!/bin/bash

docker run -it --rm --name rt1 -p 8081:8081 -p 8082:8082 -p 5555:5555 -e "JAVA_TOOL_OPTIONS=\"-agentlib:jdwp=transport=dt_socket,address=*:5555,server=y,suspend=n\"" -v $(pwd)/approveDeny.groovy:/opt/jfrog/artifactory/var/etc/artifactory/plugins/approveDeny.groovy -v $(pwd)/approveDeny.json:/opt/jfrog/artifactory/var/etc/artifactory/plugins/approveDeny.json releases-docker.jfrog.io/jfrog/artifactory-pro:7.15.3 /bin/bash
