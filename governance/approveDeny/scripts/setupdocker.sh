#!/bin/bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"

LICENSE_FILE=$1

if [ ! -f "${LICENSE_FILE}" ]; then
    echo "Usage: bash setupdocker.sh <license file>"
    exit
fi

# Take the license file and put it into a JSON object, copy that to the Artifactory service and delete the license file so we don't commit it to the repository.
sed "s/LICENSE/$(cat ${LICENSE_FILE})/" $DIR/license.rjson > $DIR/license.json
curl -u admin:password -XPOST -H'Content-Type: application/json' http://localhost:8081/artifactory/api/system/licenses -d @$DIR/license.json | jq '.status'
rm $DIR/license.json

# Update the logback.xml to support logging for this plugin
docker cp $DIR/logback.xml  rt1:/opt/jfrog/artifactory/var/etc/artifactory/logback.xml

# Create the test repository
curl -u admin:password -XPUT -H'Content-Type: application/json' "http://localhost:8081/artifactory/api/repositories/test-repo" -T $DIR/createRepo.json

# Upload an initial file to test with (NOTE: This is not a real jar it really doesn't matter)
cp $DIR/license.rjson $DIR/cool-froggy.jar
curl -XPUT -u admin:password -T $DIR/cool-froggy.jar "http://localhost:8081/artifactory/test-repo/cool-froggy.jar"
rm $DIR/cool-froggy.jar

# Creat the lib directory for the plugins dependent libraries
docker exec -it rt1 mkdir /opt/jfrog/artifactory/var/etc/artifactory/plugins/lib
